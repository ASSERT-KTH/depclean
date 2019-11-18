package se.kth.jdbl.analysis;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import se.kth.jdbl.analysis.asm.ASMDependencyAnalyzer;
import se.kth.jdbl.analysis.graph.DefaultCallGraph;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Component(role = ProjectDependencyAnalyzer.class)
public class DefaultProjectDependencyAnalyzer implements ProjectDependencyAnalyzer {
    // fields -----------------------------------------------------------------

    /**
     * ClassAnalyzer
     */
    @Requirement
    private ClassAnalyzer classAnalyzer = new DefaultClassAnalyzer();

    /**
     * DependencyAnalyzer
     */
    @Requirement
    private DependencyAnalyzer dependencyAnalyzer = new ASMDependencyAnalyzer();

    // ProjectDependencyAnalyzer methods --------------------------------------

    /*
     * @see ProjectDependencyAnalyzer#analyze(org.apache.invoke.project.MavenProject)
     */
    public ProjectDependencyAnalysis analyze(MavenProject project) throws ProjectDependencyAnalyzerException {

        try {

            // map of [dependency] -> [classes]
            Map<Artifact, Set<String>> artifactClassMap = buildArtifactClassMap(project);

            // direct dependencies of the project
            Set<Artifact> declaredArtifacts = project.getDependencyArtifacts();
            System.out.println("DIRECT DEPENDENCIES: " + declaredArtifacts);

            // transitive dependencies of the project
            Set<Artifact> transitiveArtifacts = removeAll(project.getArtifacts(), declaredArtifacts);
            System.out.println("TRANSITIVE DEPENDENCIES: " + transitiveArtifacts);

            /* ******************** bytecode analysis ********************* */

            // set of classes in project
            Set<String> builtProjectDependencyClasses = buildProjectDependencyClasses(project);
            Set<String> projectClasses = new HashSet<>(DefaultCallGraph.getProjectVertices());
//            System.out.println("PROJECT CLASSES: " + projectClasses);
            System.out.println("Number of vertices before: " + DefaultCallGraph.getVertices().size());

            Set<String> builtDependenciesDependencyClasses = buildDependenciesDependencyClasses(project);
//            HashSet dependencyClasses = new HashSet<>(DefaultCallGraph.getProjectVertices().removeAll(projectClasses));
//            System.out.println("DEPENDENCY CLASSES: " + dependencyClasses);
            System.out.println("Number of vertices after: " + DefaultCallGraph.getVertices().size());

            /* ******************** usage analysis ********************* */

            System.out.println("PROJECT CLASSES: " +projectClasses);
            // search for the dependencies used by the project
            Set<Artifact> usedArtifacts = buildUsedArtifacts(artifactClassMap, DefaultCallGraph.referencedClassMembers(projectClasses));

            /* ******************** call graph analysis ******************** */
            System.out.println("-------------------------------------------------------");
            System.out.println("USED ARTIFACTS:" + usedArtifacts);

            /* ******************** results as statically used at the bytecode *********************** */

            // for the used dependencies, get the ones that are declared
            Set<Artifact> usedDeclaredArtifacts = new LinkedHashSet<>(declaredArtifacts);
            usedDeclaredArtifacts.retainAll(usedArtifacts);

            // for the used dependencies, remove the ones that are declared
            Set<Artifact> usedUndeclaredArtifacts = new LinkedHashSet<>(usedArtifacts);
            usedUndeclaredArtifacts = removeAll(usedUndeclaredArtifacts, declaredArtifacts);

            // for the declared dependencies, get the ones that are not used
            Set<Artifact> unusedDeclaredArtifacts = new LinkedHashSet<>(declaredArtifacts);
            unusedDeclaredArtifacts = removeAll(unusedDeclaredArtifacts, usedArtifacts);

            return new ProjectDependencyAnalysis(usedDeclaredArtifacts, usedUndeclaredArtifacts, unusedDeclaredArtifacts);
        } catch (IOException exception) {
            throw new ProjectDependencyAnalyzerException("Cannot analyze dependencies", exception);
        }
    }

    private Map<Artifact, Set<String>> buildArtifactClassMap(MavenProject project) throws IOException {
        Map<Artifact, Set<String>> artifactClassMap = new LinkedHashMap<>();

        Set<Artifact> dependencyArtifacts = project.getArtifacts();

        for (Artifact artifact : dependencyArtifacts) {

            File file = artifact.getFile();

            if (file != null && file.getName().endsWith(".jar")) {
                // optimized solution for the jar case
                JarFile jarFile = new JarFile(file);

                try {
                    Enumeration<JarEntry> jarEntries = jarFile.entries();

                    Set<String> classes = new HashSet<>();

                    while (jarEntries.hasMoreElements()) {
                        String entry = jarEntries.nextElement().getName();
                        if (entry.endsWith(".class")) {
                            String className = entry.replace('/', '.');
                            className = className.substring(0, className.length() - ".class".length());
                            classes.add(className);
                        }
                    }

                    artifactClassMap.put(artifact, classes);
                } finally {
                    try {
                        jarFile.close();
                    } catch (IOException ignore) {
                        // ignore
                    }
                }
            } else if (file != null && file.isDirectory()) {
                URL url = file.toURI().toURL();
                Set<String> classes = classAnalyzer.analyze(url);

                artifactClassMap.put(artifact, classes);
            }
        }

        return artifactClassMap;
    }

    private Set<String> buildProjectDependencyClasses(MavenProject project) throws IOException {
        Set<String> dependencyClasses = new HashSet<>();
        /* paths to project compiled classes */
        String outputDirectory = project.getBuild().getOutputDirectory();
        String testOutputDirectory = project.getBuild().getTestOutputDirectory();
        /* construct the dependency classes */
        dependencyClasses.addAll(buildDependencyClasses(outputDirectory));
        dependencyClasses.addAll(buildDependencyClasses(testOutputDirectory));

        return dependencyClasses;
    }

    private Set<String> buildDependenciesDependencyClasses(MavenProject project) throws IOException {
        Set<String> dependencyClasses = new HashSet<>();
        String dependenciesDirectory = project.getBuild().getDirectory() + "/" + "dependency";
        dependencyClasses.addAll(buildDependencyClasses(dependenciesDirectory));

        return dependencyClasses;
    }

    private Set<Artifact> buildUsedArtifacts(Map<Artifact, Set<String>> artifactClassMap, Set<String> dependencyClasses) {
        Set<Artifact> usedArtifacts = new HashSet<>();

        // find for used members in each class in the dependency classes
        for (String className : dependencyClasses) {
            Artifact artifact = findArtifactForClassName(artifactClassMap, className);

            if (artifact != null) {
                usedArtifacts.add(artifact);
            }
        }

        return usedArtifacts;
    }

    /**
     * This method defines a new way to remove the artifacts by using the conflict id. We don't care about the version
     * here because there can be only 1 for a given artifact anyway.
     *
     * @param start  initial set
     * @param remove set to exclude
     * @return set with remove excluded
     */
    private Set<Artifact> removeAll(Set<Artifact> start, Set<Artifact> remove) {
        Set<Artifact> results = new LinkedHashSet<>(start.size());

        for (Artifact artifact : start) {
            boolean found = false;

            for (Artifact artifact2 : remove) {
                if (artifact.getDependencyConflictId().equals(artifact2.getDependencyConflictId())) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                results.add(artifact);
            }
        }

        return results;
    }

    private Set<String> buildDependencyClasses(String path) throws IOException {
        URL url = new File(path).toURI().toURL();

        return dependencyAnalyzer.analyze(url);
    }

    private Artifact findArtifactForClassName(Map<Artifact, Set<String>> artifactClassMap, String className) {
        for (Map.Entry<Artifact, Set<String>> entry : artifactClassMap.entrySet()) {
            if (entry.getValue().contains(className)) {
                return entry.getKey();
            }
        }
        return null;
    }

    protected Set<Artifact> buildDeclaredArtifacts(MavenProject project) {
        Set<Artifact> declaredArtifacts = project.getArtifacts();

        if (declaredArtifacts == null) {
            declaredArtifacts = Collections.emptySet();
        }

        return declaredArtifacts;
    }
}

