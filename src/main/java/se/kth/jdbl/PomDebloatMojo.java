package se.kth.jdbl;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

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
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.CollectingDependencyNodeVisitor;
import se.kth.jdbl.analysis.DefaultProjectDependencyAnalyzer;
import se.kth.jdbl.analysis.ProjectDependencyAnalysis;
import se.kth.jdbl.analysis.ProjectDependencyAnalyzer;
import se.kth.jdbl.analysis.ProjectDependencyAnalyzerException;
import se.kth.jdbl.invoke.MavenInvoker;
import se.kth.jdbl.util.JarUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

/**
 * This Maven mojo produces a clean copy of the project's pom file without bloated dependencies.
 * It is built on top of the maven-dependency-analyzer component.
 *
 * @see <a href="https://stackoverflow.com/questions/1492000/how-to-get-access-to-mavens-dependency-hierarchy-within-a-plugin"></a>
 * @see <a href="http://maven.apache.org/guides/introduction/introduction-to-optional-and-excludes-dependencies.html"></a>
 */
@Mojo(name = "debloat-pom", defaultPhase = LifecyclePhase.PACKAGE,
        requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class PomDebloatMojo extends AbstractMojo {

    //--------------------------------/
    //-------- CLASS FIELD/S --------/
    //------------------------------/

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Component
    private ProjectBuilder mavenProjectBuilder;

    @Component
    private RepositorySystem repositorySystem;

    @Component(hint = "default")
    private DependencyGraphBuilder dependencyGraphBuilder;

    //--------------------------------/
    //------- PUBLIC METHOD/S -------/
    //------------------------------/

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        getLog().info("Starting debloating POM");

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

        /* TODO consider only dependencies with compile scope */

        System.out.println("Used direct dependencies" + " [" + usedDeclaredArtifacts.size() + "]" + ": ");
        usedDeclaredArtifacts.stream().forEach(s -> System.out.println("\t" + s));

        System.out.println("Used transitive dependencies" + " [" + usedUndeclaredArtifacts.size() + "]" + ": ");
        usedUndeclaredArtifacts.stream().forEach(s -> System.out.println("\t" + s));

        System.out.println("Potentially unused direct dependencies" + " [" + unusedDeclaredArtifacts.size() + "]" + ": ");
        unusedDeclaredArtifacts.stream().forEach(s -> System.out.println("\t" + s));

        System.out.println("Potentially unused transitive dependencies" + " [" + unusedUndeclaredArtifacts.size() + "]" + ": ");
        unusedUndeclaredArtifacts.stream().forEach(s -> System.out.println("\t" + s));

        /* add used transitive as direct dependencies */
        try {
            if (!usedUndeclaredArtifacts.isEmpty()) {
                getLog().info("Adding " + usedUndeclaredArtifacts.size() + " used direct dependencies as direct dependencies.");
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
                getLog().info("Removing " + unusedDeclaredArtifacts.size() + " unused direct dependencies one-by-one.");
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
                getLog().info("Excluding " + unusedUndeclaredArtifacts.size() + " unused direct dependencies one-by-one.");
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

        /* TODO check the debloat results w.r.t the test suite */

        /* write the debloated pom file */
        try {
            Path path = Paths.get(pathToPutDebloatedPom);
            writePom(path, model);
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        getLog().info("POM debloated successfully");
        getLog().info("Debloated pom written to: " + pathToPutDebloatedPom);
    }

    //--------------------------------/
    //------ PRIVATE METHOD/S -------/
    //------------------------------/

    /**
     * Returns true if the artifact is a child of a dependency in the dependency tree.
     *
     * @param dependency
     * @param artifact
     */
    private boolean isChildren(Artifact artifact, Dependency dependency) throws DependencyGraphBuilderException {
        List<DependencyNode> dependencyNodes = getDependencyNodes();
        for (DependencyNode node : dependencyNodes) {
            Dependency dependencyNode = createDependency(node.getArtifact());
            if (dependency.getGroupId().equals(dependencyNode.getGroupId()) &&
                    dependency.getArtifactId().equals(dependencyNode.getArtifactId())) {
                // now we are in the target dependencyma
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

    private List<DependencyNode> getDependencyNodes() throws DependencyGraphBuilderException {
        ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        buildingRequest.setProject(project);
        DependencyNode rootNode = dependencyGraphBuilder.buildDependencyGraph(buildingRequest, null);
        CollectingDependencyNodeVisitor visitor = new CollectingDependencyNodeVisitor();
        rootNode.accept(visitor);
        return visitor.getNodes();
    }

    private Dependency createDependency(Artifact artifact) {
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

    private static void writePom(Path pomFile, Model model) throws IOException {
        MavenXpp3Writer writer = new MavenXpp3Writer();
        writer.write(Files.newBufferedWriter(pomFile), model);
    }
}
