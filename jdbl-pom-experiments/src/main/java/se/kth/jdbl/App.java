package se.kth.jdbl;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.SerializationUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.test.plugin.BuildTool;
import org.apache.maven.shared.test.plugin.ProjectTool;
import org.apache.maven.shared.test.plugin.RepositoryTool;
import org.apache.maven.shared.test.plugin.TestToolsException;
import org.codehaus.plexus.PlexusTestCase;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import se.kth.jdbl.analysis.ClassFileVisitorUtils;
import se.kth.jdbl.analysis.ProjectDependencyAnalysis;
import se.kth.jdbl.analysis.ProjectDependencyAnalyzer;
import se.kth.jdbl.analysis.ProjectDependencyAnalyzerException;
import se.kth.jdbl.analysis.asm.DependencyClassFileVisitor;
import se.kth.jdbl.counter.ClassMembersVisitorCounter;
import se.kth.jdbl.tree.StandardTextVisitor;
import se.kth.jdbl.tree.analysis.DependencyTreeAnalyzer;
import se.kth.jdbl.util.*;

import java.io.*;
import java.math.BigInteger;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class App extends PlexusTestCase {

    //-------------------------------/
    //-------- CLASS FIELD/S --------/
    //-------------------------------/

    private static BuildTool buildTool;
    private static File localRepo;
    private static ProjectTool projectTool;
    private static ProjectDependencyAnalyzer analyzer;

    private static final Logger LOGGER = Logger.getLogger(App.class.getName());

    //-------------------------------/
    //------- PUBLIC METHOD/S -------/
    //-------------------------------/

    public static void main(String[] args) throws Exception {
        App app = new App();
        app.setUp();

        // read the list of artifacts
        BufferedReader br = new BufferedReader(new FileReader(new File("/home/cesarsv/Documents/xperiments/df_sample10000.csv")));

        // report results files
        String resultsDir = "/home/cesarsv/Documents/xperiments/results/";

        // directories to put the artifact and its dependencies
        String artifactDir = "/home/cesarsv/Documents/xperiments/artifact/";
        String dependenciesDir = localRepo.getAbsolutePath();

        BufferedWriter bwResults = new BufferedWriter(new FileWriter(resultsDir + "results.csv", true));
        BufferedWriter bwDescription = new BufferedWriter(new FileWriter(resultsDir + "description.csv", true));

        // write csv report headers
        bwDescription.write("Artifact,NbTypes,NbFields,NbMethods,NbAnnotations,Organization,Scm,Ci,License,Description,HeightOriginalDT,HeightDebloatedDT" + "\n");
        bwResults.write("Artifact,AllDeps,Pack,Scope,Optional,Type,Used,Declared,Removable,NbTypes,NbFields,NbMethods,NbAnnotations,NbDeps,TreeLevel,InConflict" + "\n");

        bwResults.close();
        bwDescription.close();

        String artifact = br.readLine();

        // read the list of artifacts' coordinates to be analyzed
        while (artifact != null) {
//            artifact = artifact.substring(1, artifact.length() - 1);
            String[] split = artifact.split(":");
            String groupId = split[0];
            String artifactId = split[1];
            String version = split[2];

            try {
                app.execute(groupId, artifactId, version, resultsDir, artifactDir, dependenciesDir);
            } catch (TestToolsException | ProjectDependencyAnalyzerException | IOException | XmlPullParserException e) {
                artifact = br.readLine();
                continue;
            }
            artifact = br.readLine();
        }
        br.close();
    }

    public static BuildTool getBuildTool() {
        return buildTool;
    }

    public static File getLocalRepo() {
        return localRepo;
    }

    //-------------------------------/
    //------ PRIVATE METHOD/S -------/
    //-------------------------------/

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        buildTool = (BuildTool) lookup(BuildTool.ROLE);
        projectTool = (ProjectTool) lookup(ProjectTool.ROLE);
        analyzer = (ProjectDependencyAnalyzer) lookup(ProjectDependencyAnalyzer.ROLE);

        System.setProperty("maven.home", "/home/cesarsv/Documents/xperiments/dependencies");
        if (localRepo == null) {
            RepositoryTool repositoryTool = (RepositoryTool) lookup(RepositoryTool.ROLE);
            localRepo = repositoryTool.findLocalRepositoryDirectory();
            // set a custom local maven repository
            localRepo = new File("/home/cesarsv/Documents/xperiments/dependencies");
        }
    }

    private void execute(String groupId, String artifactId, String version, String resultsDir, String artifactDir, String dependenciesDir)
            throws TestToolsException, ProjectDependencyAnalyzerException, IOException, XmlPullParserException {

        MavenPluginInvoker mavenPluginInvoker = new MavenPluginInvoker();

        // remove the content of local directories
        FileUtils.cleanDirectory(new File(artifactDir));

        // set a size threshold of 10GB size (clean it if is larger that that)
        // checkDependenciesDirSize(dependenciesDir, new BigInteger("53687091200"); // 50GB

        String coordinates = groupId + ":" + artifactId + ":" + version;

        LOGGER.info("---------------------------------------------------------------------------------------------");
        LOGGER.log(Level.INFO, () -> "Processing: " + coordinates);
        LOGGER.info("---------------------------------------------------------------------------------------------");

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

                MavenProject mavenProject = null;
                try {
                    mavenProject = projectTool.readProjectWithDependencies(new File(artifactDir + "pom.xml"), localRepo);
                } catch (Exception e) {
                    LOGGER.severe("Fail to build Maven project");
                }

                processDependency(resultsDir, artifactDir, dependenciesDir, coordinates, dependencyTreePath, mavenProject);
            }
        }
    }

    private void processDependency(String resultsDir, String artifactDir, String dependenciesDir, String coordinates, String dependencyTreePath, MavenProject mavenProject)
            throws ProjectDependencyAnalyzerException, IOException, XmlPullParserException {
        if (mavenProject != null) { // the invoke project was build correctly

            Build build = new Build();
            build.setDirectory(artifactDir);
            mavenProject.setBuild(build);

            DependencyTreeAnalyzer dta = new DependencyTreeAnalyzer(dependencyTreePath);

            List<String> directDependencies = dta.getDirectDependencies();
            List<String> allDependencies = dta.getAllDependenciesCanonical(dta.getRootNode());

            LOGGER.info("analyzing dependencies usage");
            ProjectDependencyAnalysis actualAnalysis = analyzer.analyze(mavenProject);
            actualAnalysis.ignoreNonCompile();

            // used and declared dependencies"
            Set<Artifact> usedDeclaredDependencies = actualAnalysis.getUsedDeclaredArtifacts();

            // used but not undeclared dependencies
            Set<Artifact> usedUndeclaredDependencies = actualAnalysis.getUsedUndeclaredArtifacts();

            // manipulation of the pom file
            LOGGER.info("writing artifact description");
            Model pomModel = PomManipulator.readModel(new File(artifactDir + "pom.xml"));

            List<String> artifactsInConflict = new ArrayList<>();
            ArrayList<MavenDependencyBuilder> dependencies = new ArrayList<>();

            // copy original dependency tree to file
            FileUtils.copyFile(new File(artifactDir + "dependencyTree.txt"), new File(resultsDir + "trees/" + coordinates + "_original" + ".txt"));

            // deep copy of the dependency tree object
            DependencyTreeAnalyzer dtaDebloated = (DependencyTreeAnalyzer) SerializationUtils.clone(dta);

            // label all the nodes
            dtaDebloated.labelNodes(usedDeclaredDependencies, usedUndeclaredDependencies);

            // remove unused and declared artifacts
            dtaDebloated.removeUnusedArtifacts();

            // save to a file
            StandardTextVisitor dtaDebloatedTree = new StandardTextVisitor();
            dtaDebloatedTree.visit(dtaDebloated.getRootNode());
            CustomFileWriter.writeDebloatedPom(dtaDebloatedTree, resultsDir + "trees/" + coordinates + "_debloated" + ".txt");

            // write description file
            CustomFileWriter.writeArtifactProperties(resultsDir + "description.csv", pomModel, coordinates, dta, dtaDebloated);

            for (String dep : allDependencies) {

                String inConflict = "NO";

                String originalDep = dep;

                if (dep.startsWith("(")) {
                    dep = dep.substring(1, dep.length() - 1);
                    String[] tmpSplit = dep.split(" - ");
                    dep = tmpSplit[0];
                    inConflict = tmpSplit[1]
                            .replace(",", "[comma] ")
                            .replace(";", "[comma] ");
                }

                dep = dep.split(" ")[0];// manage the case "junit:junit:3.8.1:test (scope not updated to compile)"
                String[] split = dep.split(":");
                String g;
                String a;
                String t;
                String v;
                String s;
                g = split[0];
                if (split.length == 5) {
                    a = split[1];
                    t = split[2];
                    v = split[3];
                    s = split[4].split(" ")[0];
                } else { // consider the case org.jacoco:org.jacoco.agent:jar:runtime:0.7.5.201505241946:test
                    a = split[1];
                    t = split[3];
                    v = split[4];
                    s = split[5].split(" ")[0];
                }

                boolean isOptional = false;
                boolean isUsed = false;
                boolean isDeclared = false;

                for (Artifact usedDeclaredDependency : usedDeclaredDependencies) {
                    if (usedDeclaredDependency.toString().equals(dep)) {
                        isUsed = true;
                        isOptional = usedDeclaredDependency.isOptional();
                        break;
                    }
                }

                for (Artifact usedUndeclaredDependency : usedUndeclaredDependencies) {
                    if (usedUndeclaredDependency.toString().equals(dep)) {
                        isUsed = true;
                        isOptional = usedUndeclaredDependency.isOptional();
                        break;
                    }
                }

                List<Dependency> declaredDeps = pomModel.getDependencies();
                for (Dependency declaredDep : declaredDeps) {
                    if (declaredDep.getGroupId().equals(g) &&
                            declaredDep.getArtifactId().equals(a)) {
                        isDeclared = true;
                        break;
                    }
                }

                // count bytecode class members
                ClassMembersVisitorCounter.resetClassCounters();
                File file;
                if (split.length == 5) {
                    file = new File(dependenciesDir + "/" +
                            g.replace(".", "/") + "/" +
                            a + "/" +
                            v + "/" +
                            a + "-" +
                            v + ".jar");
                } else { // consider the case org.jacoco:org.jacoco.agent:jar:runtime:0.7.5.201505241946:test
                    file = new File(dependenciesDir + "/" +
                            g.replace(".", "/") + "/" +
                            a + "/" +
                            v + "/" +
                            a + "-" +
                            v + "-" + t + ".jar");
                }
                if (file.exists()) {
                    URL url = file.toURI().toURL();
                    try {
                        ClassFileVisitorUtils.accept(url, new DependencyClassFileVisitor());
                    } catch (Exception e) {
                        ClassMembersVisitorCounter.markAsNotFoundClassCounters();
                        LOGGER.log(Level.WARNING, "Something was wrong with: " + file.getAbsolutePath());
                    }
                } else {
                    ClassMembersVisitorCounter.markAsNotFoundClassCounters();
                }

                if (!inConflict.equals("NO")) {
                    artifactsInConflict.add(g + ":" + a + ":" + v);
                }

                MavenDependencyBuilder dependency = new MavenDependencyBuilder();
                dependency
                        .setCoordinates(g + ":" + a + ":" + v)
                        .setType(t)
                        .setScope(s)
                        .isOptional(isOptional)
                        .setDependencyType((directDependencies.contains(originalDep)) ? "direct" : "transitive")
                        .isUsed(isUsed)
                        .isDeclared(isDeclared)
                        .isRemovable(!dtaDebloated.getAllDependenciesCoordinates(dtaDebloated.getRootNode()).contains(g + ":" + a + ":" + v))
                        .setTreeLevel(dta.getLevel(g, a, v))
                        .setNbTypes(ClassMembersVisitorCounter.getNbVisitedTypes())
                        .setNbFields(ClassMembersVisitorCounter.getNbVisitedFields())
                        .setNbMethods(ClassMembersVisitorCounter.getNbVisitedMethods())
                        .setNbAnnotations(ClassMembersVisitorCounter.getNbVisitedAnnotations())
                        .setNbDependencies(dta.getNumberOfDependenciesOfNode(g, a, v))
                        .inConflict(inConflict);
                dependencies.add(dependency);
            }

            // save results to file
            LOGGER.info("writing artifact dependencies info ");
            CustomFileWriter.writeDependencyResults(resultsDir + "results.csv",
                    coordinates,
                    dependencies);

        }
    }

    /**
     * Removes all files in the dependencies if the size of the directory is greater than a given value.
     *
     * @param dependenciesDir The directory with the dependencies
     * @param dirSize         The size threshold
     */
    private void checkDependenciesDirSize(String dependenciesDir, BigInteger dirSize) throws IOException {
        BigInteger dependencyFolderSize = FileUtils.sizeOfAsBigInteger(new File(dependenciesDir));
        if (dependencyFolderSize.compareTo(dirSize) > 0) {
            FileUtils.cleanDirectory(new File(dependenciesDir));
        }
    }
}
