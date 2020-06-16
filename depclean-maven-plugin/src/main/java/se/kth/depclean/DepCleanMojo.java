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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

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
import se.kth.depclean.core.analysis.ProjectDependencyAnalyzer;
import se.kth.depclean.core.analysis.ProjectDependencyAnalyzerException;
import se.kth.depclean.util.JarUtils;
import se.kth.depclean.util.MavenInvoker;

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
public class DepCleanMojo extends AbstractMojo
{
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

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {

        if (skipDepClean) {
            getLog().info("Skipping DepClean plugin execution");
            return;
        }


        System.out.println(SEPARATOR);
        getLog().info("Starting DepClean dependency analysis");

        File pomFile = new File(project.getBasedir().getAbsolutePath() + "/" + "pom.xml");

        String packaging = project.getPackaging();
        if (packaging.equals("pom")) {
            getLog().info("Skipping because packaging type " + packaging + ".");
            return;
        }

        String pathToPutDebloatedPom = project.getBasedir().getAbsolutePath() + "/" + "pom-debloated.xml";

        /* Build maven model to manipulate the pom */
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
            MavenInvoker.runCommand("mvn dependency:copy-dependencies");
        } catch (IOException e) {
            getLog().error("Unable resolve all the dependencies.");
            return;
        }

        /* Decompress dependencies */
        JarUtils.decompressJars(project.getBuild().getDirectory() + "/" + "dependency");

        /* Analyze dependencies usage status */
        ProjectDependencyAnalysis projectDependencyAnalysis;
        try {
            ProjectDependencyAnalyzer dependencyAnalyzer = new DefaultProjectDependencyAnalyzer();
            projectDependencyAnalysis = dependencyAnalyzer.analyze(project);
        } catch (ProjectDependencyAnalyzerException e) {
            getLog().error("Unable to analyze dependencies.");
            return;
        }

        Set<Artifact> usedUndeclaredArtifacts = projectDependencyAnalysis.getUsedUndeclaredArtifacts();
        Set<Artifact> usedDeclaredArtifacts = projectDependencyAnalysis.getUsedDeclaredArtifacts();
        Set<Artifact> unusedDeclaredArtifacts = projectDependencyAnalysis.getUnusedDeclaredArtifacts();
        Set<Artifact> unusedUndeclaredArtifacts = project.getArtifacts();

        unusedUndeclaredArtifacts.removeAll(usedDeclaredArtifacts);
        unusedUndeclaredArtifacts.removeAll(usedUndeclaredArtifacts);
        unusedUndeclaredArtifacts.removeAll(unusedDeclaredArtifacts);

        /* Exclude dependencies with specific scopes from the DepClean analysis */
        if (!ignoreScopes.isEmpty()) {
            usedUndeclaredArtifacts = excludeScope(usedUndeclaredArtifacts);
            usedDeclaredArtifacts = excludeScope(usedDeclaredArtifacts);
            unusedDeclaredArtifacts = excludeScope(unusedDeclaredArtifacts);
            unusedUndeclaredArtifacts = excludeScope(unusedUndeclaredArtifacts);
        }

        /* Use artifacts coordinates for the report instead of the Artifact object */
        Set<String> usedDeclaredArtifactsCoordinates = new HashSet<>();
        usedDeclaredArtifacts.forEach(s -> usedDeclaredArtifactsCoordinates.add(s.getGroupId() + ":" + s.getArtifactId() + ":" + s.getVersion() + ":" + s.getScope()));

        Set<String> usedUndeclaredArtifactsCoordinates = new HashSet<>();
        usedUndeclaredArtifacts.forEach(s -> usedUndeclaredArtifactsCoordinates.add(s.getGroupId() + ":" + s.getArtifactId() + ":" + s.getVersion() + ":" + s.getScope()));

        Set<String> unusedDeclaredArtifactsCoordinates = new HashSet<>();
        unusedDeclaredArtifacts.forEach(s -> unusedDeclaredArtifactsCoordinates.add(s.getGroupId() + ":" + s.getArtifactId() + ":" + s.getVersion() + ":" + s.getScope()));

        Set<String> unusedUndeclaredArtifactsCoordinates = new HashSet<>();
        unusedUndeclaredArtifacts.forEach(s -> unusedUndeclaredArtifactsCoordinates.add(s.getGroupId() + ":" + s.getArtifactId() + ":" + s.getVersion() + ":" + s.getScope()));

        /* Ignoring dependencies from analysis */
        if (ignoreDependencies != null) {
            for (String ignoredDependency : ignoreDependencies) {
                // if the ignored dependency is an unused declared dependency then add it to the set of used declared
                // and remove it from the set of unused declared
                for (Iterator<String> i = unusedDeclaredArtifactsCoordinates.iterator(); i.hasNext(); ) {
                    String unusedDeclaredArtifact = i.next();
                    if (ignoredDependency.equals(unusedDeclaredArtifact)) {
                        usedDeclaredArtifactsCoordinates.add(unusedDeclaredArtifact);
                        i.remove();
                        break;
                    }
                }
                // if the ignored dependency is an unused undeclared dependency then add it to the set of used undeclared
                // and remove it from the set of unused undeclared
                for (Iterator<String> j = unusedUndeclaredArtifactsCoordinates.iterator(); j.hasNext(); ) {
                    String unusedUndeclaredArtifact = j.next();
                    if (ignoredDependency.equals(unusedUndeclaredArtifact)) {
                        usedUndeclaredArtifactsCoordinates.add(unusedUndeclaredArtifact);
                        j.remove();
                        break;
                    }
                }
            }
        }

        /* Printing the results to the console */
        System.out.println(" D E P C L E A N   A N A L Y S I S   R E S U L T S");
        System.out.println(SEPARATOR);

        System.out.println("Used direct dependencies" + " [" + usedDeclaredArtifactsCoordinates.size() + "]" + ": ");
        usedDeclaredArtifactsCoordinates.stream().forEach(s -> System.out.println("\t" + s));

        System.out.println("Used transitive dependencies" + " [" + usedUndeclaredArtifactsCoordinates.size() + "]" + ": ");
        usedUndeclaredArtifactsCoordinates.stream().forEach(s -> System.out.println("\t" + s));

        System.out.println("Potentially unused direct dependencies" + " [" + unusedDeclaredArtifactsCoordinates.size() + "]" + ": ");
        unusedDeclaredArtifactsCoordinates.stream().forEach(s -> System.out.println("\t" + s));

        System.out.println("Potentially unused transitive dependencies" + " [" + unusedUndeclaredArtifactsCoordinates.size() + "]" + ": ");
        unusedUndeclaredArtifactsCoordinates.stream().forEach(s -> System.out.println("\t" + s));

        if (!ignoreDependencies.isEmpty()) {
            System.out.println(SEPARATOR);
            System.out.println("Dependencies ignored in the analysis by the user" + " [" + ignoreDependencies.size() + "]" + ": ");
            ignoreDependencies.stream().forEach(s -> System.out.println("\t" + s));
        }

        /* Fail the build if there are unused dependencies */
        if (failIfUnusedDependency && (!unusedDeclaredArtifactsCoordinates.isEmpty() || !unusedUndeclaredArtifactsCoordinates.isEmpty())) {
            throw new MojoExecutionException("Build failed due to unused dependencies in the dependency tree.");
        }

        /* Writing the debloated version of the pom */
        if (createPomDebloated) {
            getLog().info("Starting debloating POM");

            /* add used transitive as direct dependencies */
            try {
                if (!usedUndeclaredArtifacts.isEmpty()) {
                    getLog().info("Adding " + usedUndeclaredArtifacts.size() + " used transitive dependencies as direct dependencies.");
                    for (Artifact usedUndeclaredArtifact : usedUndeclaredArtifacts) {
                        model.addDependency(createDependency(usedUndeclaredArtifact));
                    }
                }
            } catch (Exception e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }

            /* remove unused direct dependencies */
            try {
                if (!unusedDeclaredArtifacts.isEmpty()) {
                    getLog().info("Removing " + unusedDeclaredArtifacts.size() + " unused direct dependencies.");
                    for (Artifact unusedDeclaredArtifact : unusedDeclaredArtifacts) {
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

            /* exclude unused transitive dependencies */
            try {
                if (!unusedUndeclaredArtifacts.isEmpty()) {
                    getLog().info("Excluding " + unusedUndeclaredArtifacts.size() + " unused transitive dependencies one-by-one.");
                    for (Dependency dependency : model.getDependencies()) {
                        for (Artifact artifact : unusedUndeclaredArtifacts) {
                            if (isChildren(artifact, dependency)) {
                                System.out.println("Excluding " + artifact.toString() + " from dependency " + dependency.toString());
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

            /* write the debloated pom file */
            try {
                Path path = Paths.get(pathToPutDebloatedPom);
                writePom(path, model);
            } catch (IOException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }

            getLog().info("POM debloated successfully");
            getLog().info("pom-debloated.xml file created in: " + pathToPutDebloatedPom);
        }
    }

    private Set<Artifact> excludeScope(Set<Artifact> artifacts)
    {
        Set<Artifact> nonExcludedArtifacts = new HashSet<>();
        Iterator<Artifact> iterator = artifacts.iterator();
        while (iterator.hasNext()) {
            Artifact artifact = iterator.next();
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
    private boolean isChildren(Artifact artifact, Dependency dependency) throws DependencyGraphBuilderException
    {
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
    private List<DependencyNode> getDependencyNodes() throws DependencyGraphBuilderException
    {
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
    private Dependency createDependency(final Artifact artifact)
    {
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

    /**
     * Write pom file to the filesystem.
     *
     * @param pomFile The path to the pom.
     * @param model   The maven model to get the pom from.
     * @throws IOException In case of any IO issue.
     */
    private static void writePom(final Path pomFile, final Model model) throws IOException
    {
        MavenXpp3Writer writer = new MavenXpp3Writer();
        writer.write(Files.newBufferedWriter(pomFile), model);
    }
}
