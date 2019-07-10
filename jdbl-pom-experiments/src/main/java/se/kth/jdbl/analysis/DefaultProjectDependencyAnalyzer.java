package se.kth.jdbl.analysis;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 */
@Component(role = ProjectDependencyAnalyzer.class)
public class DefaultProjectDependencyAnalyzer
        implements ProjectDependencyAnalyzer {
    // fields -----------------------------------------------------------------

    /**
     * ClassAnalyzer
     */
    @Requirement
    private ClassAnalyzer classAnalyzer;

    /**
     * DependencyAnalyzer
     */
    @Requirement
    private DependencyAnalyzer dependencyAnalyzer;

    // ProjectDependencyAnalyzer methods --------------------------------------

    /*
     * @see ProjectDependencyAnalyzer#analyze(org.apache.invoke.project.MavenProject)
     */
    public ProjectDependencyAnalysis analyze(MavenProject project)
            throws ProjectDependencyAnalyzerException {
        try {
            Map<Artifact, Set<String>> artifactClassMap = buildArtifactClassMap(project);

            /*Map<String, Set<String>> typesClassMap = new HashMap<>();
            for (Map.Entry<Artifact, Set<String>> entry : artifactClassMap.entrySet()) {
                Artifact key = entry.getKey();
                Set<String> value = entry.getValue();
                typesClassMap.put(key.getGroupId() + ":" + key.getArtifactId() + ":" + key.getVersion(), value);
            }
            DependencyMemberCounter.setArtifactClassMap(typesClassMap);*/

            Set<String> dependencyClasses = buildDependencyClasses(project);

            Set<Artifact> declaredArtifacts = buildDeclaredArtifacts(project);

            Set<Artifact> usedArtifacts = buildUsedArtifacts(artifactClassMap, dependencyClasses);

            Set<Artifact> usedDeclaredArtifacts = new LinkedHashSet<>(declaredArtifacts);
            usedDeclaredArtifacts.retainAll(usedArtifacts);

            Set<Artifact> usedUndeclaredArtifacts = new LinkedHashSet<>(usedArtifacts);
            usedUndeclaredArtifacts = removeAll(usedUndeclaredArtifacts, declaredArtifacts);

            Set<Artifact> unusedDeclaredArtifacts = new LinkedHashSet<>(declaredArtifacts);
            unusedDeclaredArtifacts = removeAll(unusedDeclaredArtifacts, usedArtifacts);

            return new ProjectDependencyAnalysis(usedDeclaredArtifacts, usedUndeclaredArtifacts,
                    unusedDeclaredArtifacts);
        } catch (IOException exception) {
            throw new ProjectDependencyAnalyzerException("Cannot analyze dependencies", exception);
        }
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

    protected Map<Artifact, Set<String>> buildArtifactClassMap(MavenProject project)
            throws IOException {
        Map<Artifact, Set<String>> artifactClassMap = new LinkedHashMap<>();

        @SuppressWarnings("unchecked")
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
                        // ingore
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

    protected Set<String> buildDependencyClasses(MavenProject project) throws IOException {
        Set<String> dependencyClasses = new HashSet<>();

        String outputDirectory = project.getBasedir().getAbsolutePath();
        dependencyClasses.addAll(buildDependencyClasses(outputDirectory));

        return dependencyClasses;
    }

    private Set<String> buildDependencyClasses(String path) throws IOException {
        URL url = new File(path).toURI().toURL();

        return dependencyAnalyzer.analyze(url);
    }

    protected Set<Artifact> buildDeclaredArtifacts(MavenProject project) {
        @SuppressWarnings("unchecked")
        Set<Artifact> declaredArtifacts = project.getDependencyArtifacts();

        if (declaredArtifacts == null) {
            declaredArtifacts = Collections.emptySet();
        }

        return declaredArtifacts;
    }

    protected Set<Artifact> buildUsedArtifacts(Map<Artifact, Set<String>> artifactClassMap,
                                               Set<String> dependencyClasses) {
        Set<Artifact> usedArtifacts = new HashSet<Artifact>();

        for (String className : dependencyClasses) {
            Artifact artifact = findArtifactForClassName(artifactClassMap, className);

            if (artifact != null) {
                usedArtifacts.add(artifact);
            }
        }

        return usedArtifacts;
    }

    protected Artifact findArtifactForClassName(Map<Artifact, Set<String>> artifactClassMap, String className) {
        for (Map.Entry<Artifact, Set<String>> entry : artifactClassMap.entrySet()) {
            if (entry.getValue().contains(className)) {
                return entry.getKey();
            }
        }

        return null;
    }
}

