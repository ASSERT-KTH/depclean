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
import java.util.Iterator;
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
        BufferedReader br = new BufferedReader(new FileReader(new File("/home/cesarsv/Documents/xperiments/test.csv")));

        // report results files
        String resultsPath = "/home/cesarsv/Documents/xperiments/results/" + "results.csv";
        String descriptionPath = "/home/cesarsv/Documents/xperiments/results/" + "description.csv";

        BufferedWriter bwResults = new BufferedWriter(new FileWriter(resultsPath, true));
        BufferedWriter bwDescription = new BufferedWriter(new FileWriter(descriptionPath, true));

        // write csv report headers
        bwDescription.write("Artifact,Organization,Scm,Ci,License,Description" + "\n");
        bwResults.write("Artifact,AllDeps,Type,Scope,Optional,Direct,Transitive,UsedDeclared,UsedUndeclared,UnusedDeclared,UnusedUndeclared,InConflict" + "\n");

        bwResults.close();
        bwDescription.close();

        String artifact = br.readLine();

        while (artifact != null) {
//            artifact = artifact.substring(1, artifact.length() - 1);
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

            localRepo = new File("/home/cesarsv/Documents/xperiments/dependencies");
            System.setProperty("maven.home", "/home/cesarsv/Documents/xperiments/dependencies");

            LOGGER.info("Local repository: " + localRepo);
        }
        analyzer = (ProjectDependencyAnalyzer) lookup(ProjectDependencyAnalyzer.ROLE);
    }

    public void execute(String groupId, String artifactId, String version, String descriptionPath, String resultsPath)
            throws TestToolsException, ProjectDependencyAnalyzerException, IOException, XmlPullParserException {

        MavenPluginInvoker mavenPluginInvoker = new MavenPluginInvoker();

        // directories to put the artifact and its dependencies
        String artifactDir = "/home/cesarsv/Documents/xperiments/artifact/";
        String dependenciesDir = "/home/cesarsv/Documents/xperiments/dependencies/";

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
        LOGGER.info("------------------------------------------------");

        // download the artifact pom
        LOGGER.info("downloading pom");
        PomDownloader.downloadPom(artifactDir, groupId, artifactId, version);

        // copy the artifact locally
        LOGGER.info("copying artifact");
        mavenPluginInvoker.copyArtifact(artifactDir + "pom.xml", coordinates, artifactDir);

        // decompress the artifact locally if the jar file exists
        File jarFile = new File(artifactDir + artifactId + "-" + version + ".jar");
        if (jarFile.exists()) {

            // get basic dependency info from the dependency tree
            LOGGER.info("getting dependency tree");
            String dependencyTreePath = artifactDir + "dependencyTree.txt";
            mavenPluginInvoker.copyDependencyTree(artifactDir + "pom.xml", coordinates, dependencyTreePath);
            if (new File(dependencyTreePath).exists()) {

                // resolve all the dependencies
                LOGGER.info("resolving dependencies");
                mavenPluginInvoker.resolveDependencies(artifactDir + "pom.xml", coordinates);

                // copy all the dependencies locally
                LOGGER.info("copying dependencies");
                mavenPluginInvoker.copyDependencies(artifactDir + "pom.xml", coordinates, dependenciesDir);

                LOGGER.info("decompressing jar");
                JarUtils.decompressJarFile(artifactDir + "target/classes/", artifactDir + artifactId + "-" + version + ".jar");

                // build the maven project with its dependencies from the local repository
                LOGGER.info("building maven project");
                MavenProject mavenProject = projectTool.readProjectWithDependencies(new File(artifactDir + "pom.xml"), localRepo);
                Build build = new Build();
                build.setDirectory(artifactDir);
                mavenProject.setBuild(build);

                DependencyTreeAnalyzer dta = new DependencyTreeAnalyzer(dependencyTreePath);

                ArrayList<String> directDependencies = dta.getDirectDependencies();
                ArrayList<String> transitiveDependencies = dta.getTransitiveDependencies();
                ArrayList<String> allDependencies = dta.getAllDependencies();

                // analysis of dependencies
                ProjectDependencyAnalysis actualAnalysis = analyzer.analyze(mavenProject);
                actualAnalysis.ignoreNonCompile();

                // used and declared dependencies"
                Set<Artifact> usedDeclaredDependencies = actualAnalysis.getUsedDeclaredArtifacts();

                // used but undeclared dependencies
                Set<Artifact> usedUndeclaredDependencies = actualAnalysis.getUsedUndeclaredArtifacts();

                // unused but declared dependencies
                Set<Artifact> unusedDeclaredDependencies = actualAnalysis.getUnusedDeclaredArtifacts();

//                // unused and undeclared dependencies
//                ArrayList<String> ud = new ArrayList<>();
//                for (Artifact unusedDeclaredDependency : unusedDeclaredDependencies) {
//                    ud.add(unusedDeclaredDependency.toString());
//                }
//                ArrayList<String> unusedUndeclaredDependencies = dta.getArtifactsAllDependencies(ud);
//                // now remove the used and undeclared dependencies from the unused undeclared set
//                removeUsedUndeclared(usedUndeclaredDependencies, unusedUndeclaredDependencies);

                LOGGER.info("dependency analysis finished");

                // manipulation of the pom file
                LOGGER.info("writing artifact description");
                Model model = PomManipulator.readModel(new File(artifactDir + "pom.xml"));
                CustomFileWriter.writeArtifactProperties(descriptionPath, model);

                ArrayList<MavenDependency> dependencies = new ArrayList<>();
                for (String dep : allDependencies) {

                    String inConflict = "NO";

                    if(dep.startsWith("(")){
                        dep = dep.substring(1, dep.length()-1);
                        String[] tmpSplit = dep.split(" - ");
                        dep = tmpSplit[0];
                        inConflict = tmpSplit[1].replace(",", "[comma] ");
                    }

                    String[] split = dep.split(":");
                    String g = split[0];
                    String a = split[1];
                    String t = split[2];
                    String v = split[3];
                    String s = split[4];

                    boolean isOptional = false;
                    boolean isUsedDeclared = false;
                    boolean isUsedUndeclared = false;
                    boolean isUnusedDeclared = false;

                    for (Artifact usedDeclaredDependency : usedDeclaredDependencies) {
                        if (usedDeclaredDependency.toString().equals(dep)) {
                            isUsedDeclared = true;
                            isOptional = usedDeclaredDependency.isOptional();
                            break;
                        }
                    }

                    for (Artifact usedUndeclaredDependency : usedUndeclaredDependencies) {
                        if (usedUndeclaredDependency.toString().equals(dep)) {
                            isUsedUndeclared = true;
                            isOptional = usedUndeclaredDependency.isOptional();
                            break;
                        }
                    }

                    for (Artifact unusedDeclaredDependency : unusedDeclaredDependencies) {
                        if (unusedDeclaredDependency.toString().equals(dep)) {
                            isUnusedDeclared = true;
                            isOptional = unusedDeclaredDependency.isOptional();
                            break;
                        }
                    }

                    MavenDependency dependency = new MavenDependency();
                    dependency
                            .setCoordinates(g + ":" + a + ":" + v)
                            .setType(t)
                            .setScope(s)
                            .isOptional(isOptional)
                            .isDirect(directDependencies.contains(dep))
                            .isTransitive(transitiveDependencies.contains(dep))
                            .isUsedDeclared(isUsedDeclared)
                            .isUsedUndeclared(isUsedUndeclared)
                            .isUnusedDeclared(isUnusedDeclared)
                            .isUnusedUndeclared((!isUsedDeclared && !isUsedUndeclared  && !isUnusedDeclared ))
                            .inConflict(inConflict);
                    dependencies.add(dependency);
                }

                // save results to file
                LOGGER.info("writing artifact dependencies info ");
                CustomFileWriter.writeDependencyResults(resultsPath,
                        coordinates,
                        dependencies);
            }
        }
    }

//    private void removeUsedUndeclared(Set<Artifact> usedUndeclaredDependencies, ArrayList<String> unusedUndeclaredDependencies) {
//        for (Iterator<String> iterator = unusedUndeclaredDependencies.iterator(); iterator.hasNext(); ) {
//            String unusedUndeclaredDependency = iterator.next();
//            for (Artifact usedUndeclaredDependency : usedUndeclaredDependencies) {
//                String tmp = usedUndeclaredDependency.getGroupId() + ":" +
//                        usedUndeclaredDependency.getArtifactId() + ":" +
//                        usedUndeclaredDependency.getType() + ":" +
//                        usedUndeclaredDependency.getVersion() + ":" +
//                        usedUndeclaredDependency.getScope();
//                if (unusedUndeclaredDependency.equals(tmp)) {
//                    iterator.remove();
//                    break;
//                }
//            }
//        }
//    }

    public static BuildTool getBuildTool() {
        return buildTool;
    }

    public static File getLocalRepo() {
        return localRepo;
    }
}
