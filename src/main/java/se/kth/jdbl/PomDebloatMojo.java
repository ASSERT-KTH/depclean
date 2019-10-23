package se.kth.jdbl;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.repository.RepositorySystem;
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
import java.util.Set;

/**
 * This Maven mojo produces a clean copy of the project's pom file without bloated dependencies.
 * It is built on top of the maven-dependency-analyzer component.
 *
 * @see <a href="https://stackoverflow.com/questions/1492000/how-to-get-access-to-mavens-dependency-hierarchy-within-a-plugin"></a>
 * @see <a href="http://maven.apache.org/guides/introduction/introduction-to-optional-and-excludes-dependencies.html"></a>
 *
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

        System.out.println("Used declared dependencies" + " [" + usedDeclaredArtifacts.size() + "]" + ": ");
        usedDeclaredArtifacts.stream().forEach(s -> System.out.println("\t" + s));

        System.out.println("Used undeclared dependencies" + " [" + usedUndeclaredArtifacts.size() + "]" + ": ");
        usedUndeclaredArtifacts.stream().forEach(s -> System.out.println("\t" + s));

        System.out.println("Unused declared dependencies" + " [" + unusedDeclaredArtifacts.size() + "]" + ": ");
        unusedDeclaredArtifacts.stream().forEach(s -> System.out.println("\t" + s));

        System.out.println("Unused undeclared dependencies" + " [" + unusedUndeclaredArtifacts.size() + "]" + ": ");
        unusedUndeclaredArtifacts.stream().forEach(s -> System.out.println("\t" + s));

        /* add used undeclared as direct dependencies */
        try {
            if (!usedUndeclaredArtifacts.isEmpty()) {
                getLog().info("Adding " + usedUndeclaredArtifacts.size() + " used undeclared dependencies.");
                for (Artifact usedUndeclaredArtifact : usedUndeclaredArtifacts) {
                    model.addDependency(createDependency(usedUndeclaredArtifact));
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        /* remove unused declared dependencies */
        try {
            if (!unusedDeclaredArtifacts.isEmpty()) {
                getLog().info("Removing " + unusedDeclaredArtifacts.size() + " unused declared dependencies one-by-one.");
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

        /* TODO exclude unused undeclared dependencies [need to be done manually for now] */
//        try {
//            if (!unusedUndeclaredArtifacts.isEmpty()) {
//                getLog().info("Excluding " + unusedUndeclaredArtifacts.size() + " unused undeclared dependencies.");
//                for (Artifact unusedUndeclaredArtifact : unusedUndeclaredArtifacts) {
//                    unusedUndeclaredArtifact.setScope("provided");
//                    unusedUndeclaredArtifact.setOptional(true);
//                    model.addDependency(createDependency(unusedUndeclaredArtifact));
//                }
//            }
//        } catch (Exception e) {
//            throw new MojoExecutionException(e.getMessage(), e);
//        }

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
