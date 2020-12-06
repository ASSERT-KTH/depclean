/*
 * Copyright (c) 2020, CASTOR Software Research Centre (www.castor.kth.se)
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package se.kth.depclean;

import fr.dutra.tools.maven.deptree.core.ParseException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.CollectingDependencyNodeVisitor;
import se.kth.depclean.core.analysis.DefaultProjectDependencyAnalyzer;
import se.kth.depclean.core.analysis.ProjectDependencyAnalysis;
import se.kth.depclean.core.analysis.ProjectDependencyAnalyzerException;
import se.kth.depclean.util.JarUtils;
import se.kth.depclean.util.MavenInvoker;
import se.kth.depclean.util.json.ParsedDependencies;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This Maven mojo is the main class of DepClean.
 * DepClean is built on top of the maven-dependency-analyzer component.
 * It produces a clean copy of the project's pom file, without bloated dependencies.
 *
 * @see <a href="https://stackoverflow.com/questions/1492000/how-to-get-access-to-mavens-dependency-hierarchy-within-a-plugin"></a>
 * @see <a href="http://maven.apache.org/guides/introduction/introduction-to-optional-and-excludes-dependencies.html"></a>
 */
@Mojo(name = "depclean", defaultPhase = LifecyclePhase.PACKAGE,
        requiresDependencyCollection = ResolutionScope.TEST,
        requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
@Slf4j
public class DepCleanMojo extends AbstractMojo {

    private static final String SEPARATOR = "-------------------------------------------------------";

    /**
     * The Maven project to analyze.
     */
    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    /**
     * The Maven session to analyze.
     */
    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    /**
     * If this is true, DepClean creates a debloated version of the pom without unused dependencies,
     * called "debloated-pom.xml", in root of the project.
     */
    @Parameter(property = "create.pom.debloated", defaultValue = "false")
    private boolean createPomDebloated;

    /**
     * If this is true, DepClean creates a JSON file with the result of the analysis. The file is called
     * "debloat-result.json" and it is located in the root of the project.
     */
    @Parameter(property = "create.result.json", defaultValue = "false")
    private boolean createResultJson;

    /**
     * Add a list of dependencies, identified by their coordinates, to be ignored by DepClean during the analysis and
     * considered as used dependencies. Useful to override incomplete result caused by bytecode-level analysis
     * Dependency format is <code>groupId:artifactId:version</code>.
     */
    @Parameter(property = "ignore.dependencies")
    private Set<String> ignoreDependencies;

    /**
     * Ignore dependencies with specific scopes from the DepClean analysis.
     */
    @Parameter(property = "ignore.scopes")
    private Set<String> ignoreScopes;

    /**
     * If this is true, and DepClean reported any unused dependency in the dependency tree,
     * the build fails immediately after running DepClean.
     */
    @Parameter(defaultValue = "false")
    private boolean failIfUnusedDependency;

    /**
     * Skip plugin execution completely.
     */
    @Parameter(defaultValue = "false")
    private boolean skipDepClean;

    @Component
    private ProjectBuilder mavenProjectBuilder;

    @Component
    private RepositorySystem repositorySystem;

    @Component(hint = "default")
    private DependencyGraphBuilder dependencyGraphBuilder;

    /**
     * Write pom file to the filesystem.
     *
     * @param pomFile The path to the pom.
     * @param model   The maven model to get the pom from.
     * @throws IOException In case of any IO issue.
     */
    private static void writePom(final Path pomFile, final Model model) throws IOException {
        MavenXpp3Writer writer = new MavenXpp3Writer();
        writer.write(Files.newBufferedWriter(pomFile), model);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skipDepClean) {
            getLog().info("Skipping DepClean plugin execution");
            return;
        }

        printString(SEPARATOR);
        getLog().info("Starting DepClean dependency analysis");

        File pomFile = new File(project.getBasedir().getAbsolutePath() + File.separator + "pom.xml");

        String packaging = project.getPackaging();
        if (packaging.equals("pom")) {
            getLog().info("Skipping because packaging type " + packaging + ".");
            return;
        }

        /* Build Maven model to manipulate the pom */
        Model model;
        FileReader reader;
        MavenXpp3Reader mavenReader = new MavenXpp3Reader();
        try {
            reader = new FileReader(pomFile);
            model = mavenReader.read(reader);
            model.setPomFile(pomFile);
        } catch (Exception ex) {
            getLog().error("Unable to build the maven project.");
            return;
        }

        /* Copy direct dependencies locally */
        try {
            MavenInvoker.runCommand("mvn dependency:copy-dependencies -DoutputDirectory=" +
                    project.getBuild().getDirectory() + File.separator + "dependency");
        } catch (IOException e) {
            getLog().error("Unable to resolve all the dependencies.");
            return;
        }

        // TODO remove this workaround later
        if (new File(project.getBuild().getDirectory() + File.separator + "libs").exists()) {
            try {
                FileUtils.copyDirectory(new File(project.getBuild().getDirectory() + File.separator + "libs"),
                        new File(project.getBuild().getDirectory() + File.separator + "dependency")
                                       );
            } catch (IOException e) {
                getLog().error("Error copying directory libs to dependency");
            }
        }

        /* Get the size of all the dependencies */
        Map<String, Long> sizeOfDependencies = new HashMap<>();
        Iterator<File> iterator = FileUtils.iterateFiles(
                new File(
                        project.getBuild().getDirectory() + File.separator
                                + "dependency"), new String[]{"jar"}, true);
        while (iterator.hasNext()) {
            File file = iterator.next();
            sizeOfDependencies.put(file.getName(), FileUtils.sizeOf(file));
        }

        /* Decompress dependencies */
        String dependencyDirectoryName = project.getBuild().getDirectory() + "/" + "dependency";
        File dependencyDirectory = new File(dependencyDirectoryName);
        if (dependencyDirectory.exists()) {
            JarUtils.decompressJars(dependencyDirectoryName);
        }

        /* Analyze dependencies usage status */
        ProjectDependencyAnalysis projectDependencyAnalysis;
        DefaultProjectDependencyAnalyzer dependencyAnalyzer = new DefaultProjectDependencyAnalyzer();
        try {
            projectDependencyAnalysis = dependencyAnalyzer.analyze(project);
        } catch (ProjectDependencyAnalyzerException e) {
            getLog().error("Unable to analyze dependencies.");
            return;
        }

        Set<Artifact> usedTransitiveArtifacts = projectDependencyAnalysis.getUsedUndeclaredArtifacts();
        Set<Artifact> usedDirectArtifacts = projectDependencyAnalysis.getUsedDeclaredArtifacts();
        Set<Artifact> unusedDirectArtifacts = projectDependencyAnalysis.getUnusedDeclaredArtifacts();
        Set<Artifact> unusedTransitiveArtifacts = project.getArtifacts();

        unusedTransitiveArtifacts.removeAll(usedDirectArtifacts);
        unusedTransitiveArtifacts.removeAll(usedTransitiveArtifacts);
        unusedTransitiveArtifacts.removeAll(unusedDirectArtifacts);

        /* Exclude dependencies with specific scopes from the DepClean analysis */
        if (!ignoreScopes.isEmpty()) {
            usedTransitiveArtifacts = excludeScope(usedTransitiveArtifacts);
            usedDirectArtifacts = excludeScope(usedDirectArtifacts);
            unusedDirectArtifacts = excludeScope(unusedDirectArtifacts);
            unusedTransitiveArtifacts = excludeScope(unusedTransitiveArtifacts);
        }

        /* Use artifacts coordinates for the report instead of the Artifact object */

        // List of dependencies declared in the POM
        List<Dependency> dependencies = model.getDependencies();
        Set<String> declaredArtifactsGAs = new HashSet<>();
        for (Dependency dep : dependencies) {
            declaredArtifactsGAs.add(dep.getGroupId() + ":" + dep.getArtifactId());
        }

        // --- used dependencies
        Set<String> usedDirectArtifactsCoordinates = new HashSet<>();
        Set<String> usedInheritedArtifactsCoordinates = new HashSet<>();
        Set<String> usedTransitiveArtifactsCoordinates = new HashSet<>();

        for (Artifact artifact : usedDirectArtifacts) {
            String artifactGA = artifact.getGroupId() + ":" + artifact.getArtifactId();
            String artifactGAVS = artifactGA + ":" + artifact.toString().split(":")[3] + ":" + artifact.toString().split(":")[4];
            if (declaredArtifactsGAs.contains(artifactGA)) {
                // the artifact is declared in the pom
                usedDirectArtifactsCoordinates.add(artifactGAVS);
            } else {
                // the artifact is inherited
                usedInheritedArtifactsCoordinates.add(artifactGAVS);
            }
        }

        // TODO Fix: The used transitive dependencies induced by inherited dependencies should be considered as used inherited
        for (Artifact artifact : usedTransitiveArtifacts) {
            String artifactGA = artifact.getGroupId() + ":" + artifact.getArtifactId();
            String artifactGAVS = artifactGA + ":" + artifact.toString().split(":")[3] + ":" + artifact.toString().split(":")[4];
            usedTransitiveArtifactsCoordinates.add(artifactGAVS);
        }

        // --- unused dependencies
        Set<String> unusedDirectArtifactsCoordinates = new HashSet<>();
        Set<String> unusedInheritedArtifactsCoordinates = new HashSet<>();
        Set<String> unusedTransitiveArtifactsCoordinates = new HashSet<>();

        for (Artifact artifact : unusedDirectArtifacts) {
            String artifactGA = artifact.getGroupId() + ":" + artifact.getArtifactId();
            String artifactGAVS = artifactGA + ":" + artifact.toString().split(":")[3] + ":" + artifact.toString().split(":")[4];
            if (declaredArtifactsGAs.contains(artifactGA)) {
                // the artifact is declared in the pom
                unusedDirectArtifactsCoordinates.add(artifactGAVS);
            } else {
                // the artifact is inherited
                unusedInheritedArtifactsCoordinates.add(artifactGAVS);
            }
        }

        // TODO Fix: The unused transitive dependencies induced by inherited dependencies should be considered as unused inherited
        for (Artifact artifact : unusedTransitiveArtifacts) {
            String artifactGA = artifact.getGroupId() + ":" + artifact.getArtifactId();
            String artifactGAVS = artifactGA + ":" + artifact.toString().split(":")[3] + ":" + artifact.toString().split(":")[4];
            unusedTransitiveArtifactsCoordinates.add(artifactGAVS);
        }

        /* Ignoring dependencies from the analysis */
        if (ignoreDependencies != null) {
            for (String ignoredDependency : ignoreDependencies) {
                // if the ignored dependency is an unused direct dependency then add it to the set of used direct
                // and remove it from the set of unused direct
                for (Iterator<String> i = unusedDirectArtifactsCoordinates.iterator(); i.hasNext(); ) {
                    String unusedDirectArtifact = i.next();
                    if (ignoredDependency.equals(unusedDirectArtifact)) {
                        usedDirectArtifactsCoordinates.add(unusedDirectArtifact);
                        i.remove();
                        break;
                    }
                }
                // if the ignored dependency is an unused inherited dependency then add it to the set of used inherited
                // and remove it from the set of unused inherited
                for (Iterator<String> j = unusedInheritedArtifactsCoordinates.iterator(); j.hasNext(); ) {
                    String unusedInheritedArtifact = j.next();
                    if (ignoredDependency.equals(unusedInheritedArtifact)) {
                        usedInheritedArtifactsCoordinates.add(unusedInheritedArtifact);
                        j.remove();
                        break;
                    }
                }
                // if the ignored dependency is an unused transitive dependency then add it to the set of used transitive
                // and remove it from the set of unused transitive
                for (Iterator<String> j = unusedTransitiveArtifactsCoordinates.iterator(); j.hasNext(); ) {
                    String unusedTransitiveArtifact = j.next();
                    if (ignoredDependency.equals(unusedTransitiveArtifact)) {
                        usedTransitiveArtifactsCoordinates.add(unusedTransitiveArtifact);
                        j.remove();
                        break;
                    }
                }
            }
        }

        /* Printing the results to the console */
        printString(" D E P C L E A N   A N A L Y S I S   R E S U L T S");
        printString(SEPARATOR);

        printString("Used direct dependencies".toUpperCase() + " [" + usedDirectArtifactsCoordinates.size() + "]" + ": ");
        printDependencies(sizeOfDependencies, usedDirectArtifactsCoordinates);

        printString("Used inherited dependencies".toUpperCase() + " [" + usedInheritedArtifactsCoordinates.size() + "]" + ": ");
        printDependencies(sizeOfDependencies, usedInheritedArtifactsCoordinates);

        printString("Used transitive dependencies".toUpperCase() + " [" + usedTransitiveArtifactsCoordinates.size() + "]" + ": ");
        printDependencies(sizeOfDependencies, usedTransitiveArtifactsCoordinates);

        printString("Potentially unused direct dependencies".toUpperCase() + " [" + unusedDirectArtifactsCoordinates.size() + "]" + ": ");
        printDependencies(sizeOfDependencies, unusedDirectArtifactsCoordinates);

        printString("Potentially unused inherited dependencies".toUpperCase() + " [" + unusedInheritedArtifactsCoordinates.size() + "]" + ": ");
        printDependencies(sizeOfDependencies, unusedInheritedArtifactsCoordinates);

        printString("Potentially unused transitive dependencies".toUpperCase() + " [" + unusedTransitiveArtifactsCoordinates.size() + "]" + ": ");
        printDependencies(sizeOfDependencies, unusedTransitiveArtifactsCoordinates);

        if (!ignoreDependencies.isEmpty()) {
            printString(SEPARATOR);
            printString("Dependencies ignored in the analysis by the user" + " [" + ignoreDependencies.size() + "]" + ": ");
            ignoreDependencies.stream().forEach(s -> printString("\t" + s));
        }

        /* Fail the build if there are unused dependencies */
        if (failIfUnusedDependency && (!unusedDirectArtifactsCoordinates.isEmpty() || !unusedTransitiveArtifactsCoordinates.isEmpty())) {
            throw new MojoExecutionException("Build failed due to unused dependencies in the dependency tree.");
        }

        /* Writing the debloated version of the pom */
        if (createPomDebloated) {
            getLog().info("Starting debloating POM");

            /* Add used transitive as direct dependencies */
            try {
                if (!usedTransitiveArtifacts.isEmpty()) {
                    getLog().info("Adding " + unusedTransitiveArtifactsCoordinates.size() + " used transitive dependencies as direct dependencies.");
                    for (Artifact usedUndeclaredArtifact : usedTransitiveArtifacts) {
                        model.addDependency(createDependency(usedUndeclaredArtifact));
                    }
                }
            } catch (Exception e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }

            /* Remove unused direct dependencies */
            try {
                if (!unusedDirectArtifacts.isEmpty()) {
                    getLog().info("Removing " + unusedDirectArtifactsCoordinates.size() + " unused direct dependencies.");
                    for (Artifact unusedDeclaredArtifact : unusedDirectArtifacts) {
                        for (Dependency dependency : model.getDependencies()) {
                            if (dependency.getGroupId().equals(unusedDeclaredArtifact.getGroupId()) &&
                                    dependency.getArtifactId().equals(unusedDeclaredArtifact.getArtifactId())) {
                                model.removeDependency(dependency);
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }

            /* Exclude unused transitive dependencies */
            try {
                if (!unusedTransitiveArtifacts.isEmpty()) {
                    getLog().info("Excluding " + unusedTransitiveArtifacts.size() + " unused transitive dependencies one-by-one.");
                    for (Dependency dependency : model.getDependencies()) {
                        for (Artifact artifact : unusedTransitiveArtifacts) {
                            if (isChildren(artifact, dependency)) {
                                getLog().info("Excluding " + artifact.toString() + " from dependency " + dependency.toString());
                                Exclusion exclusion = new Exclusion();
                                exclusion.setGroupId(artifact.getGroupId());
                                exclusion.setArtifactId(artifact.getArtifactId());
                                dependency.addExclusion(exclusion);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }

            /* Write the debloated pom file */
            String pathToDebloatedPom = project.getBasedir().getAbsolutePath() + File.separator + "pom-debloated.xml";
            try {
                Path path = Paths.get(pathToDebloatedPom);
                writePom(path, model);
            } catch (IOException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }

            getLog().info("POM debloated successfully");
            getLog().info("pom-debloated.xml file created in: " + pathToDebloatedPom);
        }


        /* Writing the JSON file with the debloat results */
        if (createResultJson) {
            String pathToJsonFile = project.getBasedir().getAbsolutePath() + File.separator + "depclean-results.json";
            String treeFile = project.getBuild().getDirectory() + File.separator + "tree.txt";
            /* Copy direct dependencies locally */
            try {
                MavenInvoker.runCommand("mvn dependency:tree -DoutputFile=" + treeFile + " -Dverbose=true");
            } catch (IOException e) {
                getLog().error("Unable to generate dependency tree.");
                return;
            }
            File classUsageFile = new File(project.getBasedir().getAbsolutePath() + File.separator + "class-usage.csv");
            try {
                FileUtils.write(classUsageFile, "ProjectClass,DependencyClass,Dependency\n", Charset.defaultCharset());
            } catch (IOException e) {
                getLog().error("Error writing the CSV header.");
            }
            ParsedDependencies parsedDependencies = new ParsedDependencies(
                    treeFile,
                    sizeOfDependencies,
                    dependencyAnalyzer,
                    usedDirectArtifactsCoordinates,
                    usedInheritedArtifactsCoordinates,
                    usedTransitiveArtifactsCoordinates,
                    unusedDirectArtifactsCoordinates,
                    unusedInheritedArtifactsCoordinates,
                    unusedTransitiveArtifactsCoordinates,
                    classUsageFile
            );
            try {
                FileUtils.write(new File(pathToJsonFile), parsedDependencies.parseTreeToJSON(), Charset.defaultCharset());
                getLog().info("depclean-results.json file created in: " + pathToJsonFile);
            } catch (ParseException | IOException e) {
                getLog().error("Unable to generate JSON file.");
            }
        }
    }

    /**
     * Print the status of the depenencies to the standard output.
     * The format is: "[coordinates][scope] [(size)]"
     *
     * @param sizeOfDependencies A map with the size of the dependencies.
     * @param dependencies The set dependencies to print.
     */
    private void printDependencies(Map<String, Long> sizeOfDependencies, Set<String> dependencies) {
        dependencies
                .stream()
                .sorted(Comparator.comparing(o -> getSizeOfDependency(sizeOfDependencies, o)))
                .collect(Collectors.toCollection(LinkedList::new))
                .descendingIterator()
                .forEachRemaining(s -> printString("\t" + s + " (" + getSize(s, sizeOfDependencies) + ")"));
    }

    /**
     * Utility method to obtain the size of a dependency from a map of dependency -> size. If the size of the dependency
     * cannot be obtained form the map (no key with the name of the dependency exists), then it returns 0.
     *
     * @param sizeOfDependencies A map of dependency -> size.
     * @param dependency The coordinates of a dependency.
     * @return The size of the dependency if its name is a key in the map, otherwise it returns 0.
     */
    private Long getSizeOfDependency(Map<String, Long> sizeOfDependencies, String dependency) {
        Long size = sizeOfDependencies.get(dependency.split(":")[1] + "-" + dependency.split(":")[2] + ".jar");
        if (size != null) {
            return size;
        } else {
            // The name of the dependency does not match with the name of the download jar, so we keep assume the size
            // cannot be obtained and return 0.
            return Long.valueOf(0);
        }
    }

    /**
     * Get the size of the dependency in human readable format.
     *
     * @param dependency The dependency.
     * @param sizeOfDependencies A map with the size of the dependencies, keys are stored as the downloaded jar file
     *                           i.e., [artifactId]-[version].jar
     * @return The human readable representation of the dependency size.
     */
    private String getSize(String dependency, Map<String, Long> sizeOfDependencies) {
        String dep = dependency.split(":")[1] + "-" + dependency.split(":")[2] + ".jar";
        if (sizeOfDependencies.containsKey(dep)) {
            return FileUtils.byteCountToDisplaySize(sizeOfDependencies.get(dep));
        } else {
            // The size cannot be obtained.
            return "size unknown";
        }
    }

    /**
     * Exclude artifacts with specific scopes from the analysis.
     *
     * @param artifacts The set of artifacts to analyze.
     * @return The set of artifacts for which the scope has not been excluded.
     */
    private Set<Artifact> excludeScope(Set<Artifact> artifacts) {
        Set<Artifact> nonExcludedArtifacts = new HashSet<>();
        for (Artifact artifact : artifacts) {
            if (!ignoreScopes.contains(artifact.getScope())) {
                nonExcludedArtifacts.add(artifact);
            }
        }
        return nonExcludedArtifacts;
    }

    /**
     * Determine if an artifact is a direct or transitive child of a dependency.
     *
     * @param artifact   The artifact.
     * @param dependency The dependency
     * @return true if the artifact is a child of a dependency in the dependency tree.
     * @throws DependencyGraphBuilderException If the graph cannot be constructed.
     */
    private boolean isChildren(Artifact artifact, Dependency dependency) throws DependencyGraphBuilderException {
        List<DependencyNode> dependencyNodes = getDependencyNodes();
        for (DependencyNode node : dependencyNodes) {
            Dependency dependencyNode = createDependency(node.getArtifact());
            if (dependency.getGroupId().equals(dependencyNode.getGroupId()) &&
                    dependency.getArtifactId().equals(dependencyNode.getArtifactId())) {
                // now we are in the target dependency
                for (DependencyNode child : node.getChildren()) {
                    if (child.getArtifact().equals(artifact)) {
                        // the dependency contains the artifact as a child node
                        return true;
                    }

                }
            }
        }
        return false;
    }

    /**
     * This method returns a list of dependency nodes from a graph of dependency tree.
     *
     * @return The nodes in the dependency graph.
     * @throws DependencyGraphBuilderException If the graph cannot be built.
     */
    private List<DependencyNode> getDependencyNodes() throws DependencyGraphBuilderException {
        ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        buildingRequest.setProject(project);
        DependencyNode rootNode = dependencyGraphBuilder.buildDependencyGraph(buildingRequest, null);
        CollectingDependencyNodeVisitor visitor = new CollectingDependencyNodeVisitor();
        rootNode.accept(visitor);
        return visitor.getNodes();
    }

    /**
     * This method creates a {@link org.apache.maven.model.Dependency} object from a
     * Maven {@link org.apache.maven.artifact.Artifact}.
     *
     * @param artifact The artifact to create the dependency.
     * @return The Dependency object.
     */
    private Dependency createDependency(final Artifact artifact) {
        Dependency dependency = new Dependency();
        dependency.setGroupId(artifact.getGroupId());
        dependency.setArtifactId(artifact.getArtifactId());
        dependency.setVersion(artifact.getVersion());
        if (artifact.hasClassifier()) {
            dependency.setClassifier(artifact.getClassifier());
        }
        dependency.setOptional(artifact.isOptional());
        dependency.setScope(artifact.getScope());
        dependency.setType(artifact.getType());
        return dependency;
    }

    private void printString(String string) {
        System.out.println(string); //NOSONAR avoid a warning of non-used logger
    }
}
