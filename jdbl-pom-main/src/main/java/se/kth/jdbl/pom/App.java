package se.kth.jdbl.pom;

import analyzer.ProjectDependencyAnalysis;
import analyzer.ProjectDependencyAnalyzer;
import analyzer.ProjectDependencyAnalyzerException;
import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.test.plugin.BuildTool;
import org.apache.maven.shared.test.plugin.ProjectTool;
import org.apache.maven.shared.test.plugin.RepositoryTool;
import org.apache.maven.shared.test.plugin.TestToolsException;
import org.codehaus.plexus.PlexusTestCase;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Set;
import java.util.logging.Logger;

public class App extends PlexusTestCase {

    private static final Logger LOGGER = Logger.getLogger(App.class.getName());
    private static BuildTool buildTool;
    private static File localRepo;
    private static ProjectTool projectTool;
    private static ProjectDependencyAnalyzer analyzer;

    public static void main(String[] args) throws Exception {
        App app = new App();
        app.setUp();

        // read the list of artifacts
        BufferedReader br = new BufferedReader(new FileReader(new File("/home/cesarsv/Documents/xperiments/artifacts_ten_usages.csv")));

        // report results files
        String resultsPath = "/home/cesarsv/Documents/xperiments/" + "results.csv";
        String descriptionPath = "/home/cesarsv/Documents/xperiments/" + "description.csv";

        BufferedWriter bwResults = new BufferedWriter(new FileWriter(resultsPath, true));
        BufferedWriter bwDescription = new BufferedWriter(new FileWriter(descriptionPath, true));

        // write csv report headers
        bwDescription.write("Artifact,Organization,Scm,Ci,License,Description" + "\n");
        bwResults.write("Artifact,UsedDeclared,UsedUndeclared,UnusetDeclared,UnusedUndeclared,DirectDep,TransDep,AllDep" + "\n");

        bwResults.close();
        bwDescription.close();

        String artifact = br.readLine();

        while (artifact != null) {
            artifact = artifact.substring(1, artifact.length() - 1);
            String[] split = artifact.split(":");
            String groupId = split[0];
            String artifactId = split[1];
            String version = split[2];

            try {
                app.execute(groupId, artifactId, version, descriptionPath, resultsPath);
            } catch (TestToolsException | ProjectDependencyAnalyzerException | IOException | XmlPullParserException e) {
                artifact = br.readLine();
                continue;
            }
            artifact = br.readLine();
        }

        br.close();

    }

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

    public void execute(String groupId, String artifactId, String version, String descriptionPath, String resultsPath)
            throws TestToolsException, ProjectDependencyAnalyzerException, IOException, XmlPullParserException {

        MavenPluginInvoker mavenPluginInvoker = new MavenPluginInvoker();

        // directories to put the artifact and its dependencies
        String artifactDir = "/home/cesarsv/Documents/tmp/artifact/";
        String dependenciesDir = "/home/cesarsv/Documents/tmp/dependencies/";

        // remove the content of local directories
        FileUtils.cleanDirectory(new File(artifactDir));

        // set a size threshold of 10GB size (clean it if is larger that that)
        BigInteger dependencyFolderSize = FileUtils.sizeOfAsBigInteger(new File(dependenciesDir));
        if (dependencyFolderSize.compareTo(new BigInteger("10737418240")) > 0) {
            FileUtils.cleanDirectory(new File(dependenciesDir));
        }

        String coordinates = groupId + ":" + artifactId + ":" + version;

        LOGGER.info("------------------------------------------------");
        LOGGER.info("Processing: " + coordinates);

        // download the artifact pom
        LOGGER.info("downloading pom");
        PomDownloader.downloadPom(artifactDir, groupId, artifactId, version);

        // copy the artifact locally
        LOGGER.info("copying artifact");
        mavenPluginInvoker.copyArtifact(artifactDir + "pom.xml", coordinates, artifactDir);

        // decompress the artifact locally if the jar file exists
        File jarFile = new File(artifactDir + artifactId + "-" + version + ".jar");
        if (jarFile.exists()) {

            // resolve all the dependencies
            LOGGER.info("resolving dependencies");
            mavenPluginInvoker.resolveDependencies(artifactDir + "pom.xml", coordinates);

            // copy all the dependencies locally
            LOGGER.info("copying dependencies");
            mavenPluginInvoker.copyDependencies(artifactDir + "pom.xml", coordinates, dependenciesDir);

            LOGGER.info("decompressing jar");
            JarUtils.decompressJarFile(artifactDir, artifactDir + artifactId + "-" + version + ".jar");

            // build the maven project with its dependencies from the local repository
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

            // Used and declared dependencies"
            Set<Artifact> usedDeclaredDependencies = actualAnalysis.getUsedDeclaredArtifacts();

            // Used but undeclared dependencies
            Set<Artifact> usedUndeclaredDependencies = actualAnalysis.getUsedUndeclaredArtifacts();

            // Unused but declared dependencies
            Set<Artifact> unusedDeclaredDependencies = actualAnalysis.getUnusedDeclaredArtifacts();

            // Unused and undeclared dependencies
            ArrayList<String> ud = new ArrayList<>();
            for (Artifact unusedDeclaredDependency : unusedDeclaredDependencies) {
                ud.add(unusedDeclaredDependency.toString());
            }
            ArrayList<String> unusedUndeclaredDependencies = dta.getArtifactsAllDependencies(ud);

            LOGGER.info("dependency analysis finished");

            // manipulation of the pom file
            LOGGER.info("writing artifact description");
            Model model = PomManipulator.readModel(new File(artifactDir + "pom.xml"));
            CustomFileWriter.writeArtifactProperties(descriptionPath, model);

            // save results to file
            LOGGER.info("writing artifact dependencies info ");
            CustomFileWriter.writeDependencyResults(resultsPath,
                    coordinates,
                    usedDeclaredDependencies,
                    usedUndeclaredDependencies,
                    unusedDeclaredDependencies,
                    unusedUndeclaredDependencies,
                    directDependencies,
                    transitiveDependencies,
                    allDependencies);
        }
    }

    public static BuildTool getBuildTool() {
        return buildTool;
    }

    public static File getLocalRepo() {
        return localRepo;
    }
}
