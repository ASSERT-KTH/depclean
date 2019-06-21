package se.kth.jdbl.pom.main;

import analyzer.ProjectDependencyAnalysis;
import analyzer.ProjectDependencyAnalyzer;
import analyzer.ProjectDependencyAnalyzerException;
import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.test.plugin.BuildTool;
import org.apache.maven.shared.test.plugin.ProjectTool;
import org.apache.maven.shared.test.plugin.RepositoryTool;
import org.apache.maven.shared.test.plugin.TestToolsException;
import org.codehaus.plexus.PlexusTestCase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.logging.Logger;

public class Main extends PlexusTestCase {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    private static BuildTool buildTool;
    private ProjectTool projectTool;
    private ProjectDependencyAnalyzer analyzer;
    private static File localRepo;

    protected void setUp() throws Exception {
        super.setUp();

        buildTool = (BuildTool) lookup(BuildTool.ROLE);

        projectTool = (ProjectTool) lookup(ProjectTool.ROLE);

        if (localRepo == null) {
            RepositoryTool repositoryTool = (RepositoryTool) lookup(RepositoryTool.ROLE);
            localRepo = repositoryTool.findLocalRepositoryDirectory();

            // set a custom local maven repository
            localRepo = new File("/home/cesarsv/Documents/tmp/dependencies");
            System.setProperty("maven.home", "/home/cesarsv/Documents/tmp/dependencies");

            LOGGER.info("Local repository: " + localRepo);
        }

        analyzer = (ProjectDependencyAnalyzer) lookup(ProjectDependencyAnalyzer.ROLE);
    }

    public void testJarWithTestDependency() throws TestToolsException, ProjectDependencyAnalyzerException, IOException {

        MavenPluginInvoker mavenPluginInvoker = new MavenPluginInvoker();

        // directories to put the artifact and its dependencies
        String artifactDir = "/home/cesarsv/Documents/tmp/artifact/";
        String dependenciesDir = "/home/cesarsv/Documents/tmp/dependencies/";

        // remove the content of local directories
        FileUtils.cleanDirectory(new File(artifactDir));
        FileUtils.cleanDirectory(new File(dependenciesDir));

        // artifact separated coordinates
        String groupId = "org.apache.ws.commons.axiom";
        String artifactId = "axiom-api";
        String version = "1.2.20";
        String coordinates = groupId + ":" + artifactId + ":" + version;

        // download the artifact pom
        PomDownloader.downloadPom(artifactDir, groupId, artifactId, version);

        // resolve all the dependencies
        mavenPluginInvoker.resolveDependencies(artifactDir + "pom.xml", coordinates);

        // copy the artifact locally
        mavenPluginInvoker.copyArtifact(artifactDir + "pom.xml", coordinates, artifactDir);

        // copy all the dependencies locally
        mavenPluginInvoker.copyDependencies(artifactDir + "pom.xml", coordinates, dependenciesDir);

        // decompress the artifact locally
        JarUtils.decompressJarFile(artifactDir, artifactDir + artifactId + "-" + version + ".jar");

        // build the maven project and its dependencies from the local repository
        MavenProject mavenProject = projectTool.readProjectWithDependencies(new File(artifactDir + "pom.xml"), localRepo);
        Build build = new Build();
        build.setDirectory(artifactDir);
        mavenProject.setBuild(build);

        // get basic dependency info from the dependency tree
        String dependencyTreePath = dependenciesDir + "dependencyTree.txt";
        mavenPluginInvoker.copyDependencyTree(artifactDir + "pom.xml", coordinates, dependencyTreePath);
        DependencyTreeAnalyzer dta = new DependencyTreeAnalyzer(dependencyTreePath);
        ArrayList<String> directDependencies = dta.getDirectDependencies();
        ArrayList<String> transitiveDependencies = dta.getTransitiveDependencies();
        ArrayList<String> allDependencies = dta.getAllDependencies();

        // output the analysis of dependencies
        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze(mavenProject);
        actualAnalysis.ignoreNonCompile();

        System.out.println("Used and declared dependencies");
        Set<Artifact> usedDeclaredDependencies = actualAnalysis.getUsedDeclaredArtifacts();
        usedDeclaredDependencies.forEach(x -> System.out.println("\t" + x));

        System.out.println("Used but undeclared dependencies");
        Set<Artifact> usedUndeclaredDependencies = actualAnalysis.getUsedUndeclaredArtifacts();
        usedUndeclaredDependencies.forEach(x -> System.out.println("\t" + x));

        System.out.println("Unused but declared dependencies");
        Set<Artifact> unusedDeclaredDependencies = actualAnalysis.getUnusedDeclaredArtifacts();
        unusedDeclaredDependencies.forEach(x -> System.out.println("\t" + x));

        System.out.println("Unused and undeclared dependencies");
        ArrayList<String > ud = new ArrayList<>();
        for (Artifact unusedDeclaredDependency : unusedDeclaredDependencies) {
            ud.add(unusedDeclaredDependency.toString());
        }
        ArrayList<String> unusedUndeclaredDependencies = dta.getArtifactsAllDependencies(ud);
        unusedUndeclaredDependencies.forEach(x -> System.out.println("\t" + x));

        // save results to file
        DependenciesWriter.writeResults("/home/cesarsv/Documents/" + "results.csv",
                coordinates,
                usedDeclaredDependencies,
                usedUndeclaredDependencies,
                unusedDeclaredDependencies,
                directDependencies,
                transitiveDependencies,
                allDependencies);
    }

    public static BuildTool getBuildTool() {
        return buildTool;
    }

    public static File getLocalRepo() {
        return localRepo;
    }
}
