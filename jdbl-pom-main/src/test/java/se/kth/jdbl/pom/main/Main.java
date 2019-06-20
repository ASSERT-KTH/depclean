package se.kth.jdbl.pom.main;

import analyzer.ProjectDependencyAnalysis;
import analyzer.ProjectDependencyAnalyzer;
import analyzer.ProjectDependencyAnalyzerException;
import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.test.plugin.BuildTool;
import org.apache.maven.shared.test.plugin.ProjectTool;
import org.apache.maven.shared.test.plugin.RepositoryTool;
import org.apache.maven.shared.test.plugin.TestToolsException;
import org.codehaus.plexus.PlexusTestCase;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;

public class Main extends PlexusTestCase {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    private BuildTool buildTool;
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

    public void testGetSystemProperties() {
        Properties properties = System.getProperties();
        properties.forEach((k, v) -> System.out.println(k + ":" + v));
    }

    public void testJarWithTestDependency() throws TestToolsException, ProjectDependencyAnalyzerException, IOException, CloneNotSupportedException {

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
        downloadPom(artifactDir, groupId, artifactId, version);

        // resolve all the dependencies
        resolveDependencies(artifactDir + "pom.xml", coordinates);

        // copy the artifact locally
        copyArtifact(artifactDir + "pom.xml", coordinates, artifactDir);

        // copy all the dependencies locally
        copyDependencies(artifactDir + "pom.xml", coordinates, dependenciesDir);

        // decompress the artifact locally
        decompressJarFile(artifactDir, artifactDir + artifactId + "-" + version + ".jar");

        // build the maven project and its dependencies from the local repository
        MavenProject mavenProject = projectTool.readProjectWithDependencies(new File(artifactDir + "pom.xml"), localRepo);
        Build build = new Build();
        build.setDirectory(artifactDir);
        mavenProject.setBuild(build);

        // output direct, transitive, and all dependencies
        System.out.println("Direct dependencies");
        Set<Dependency> tmp = new HashSet(mavenProject.getDependencies());
        Set<String> directDependencies = new HashSet<>();
        for (Dependency d : tmp) {
            directDependencies.add(d.getGroupId() + ":" + d.getArtifactId() + ":" + d.getType() + ":" + d.getVersion() + ":" + d.getScope());
        }

        System.out.println("All dependencies");
        listAllDependencies(artifactDir + "pom.xml", coordinates, dependenciesDir + "allDependencies.txt");
        // read the list of dependencies from the file
        List<String> allLines = Files.readAllLines(Paths.get(dependenciesDir + "allDependencies.txt"));
        ArrayList<String> allDependenciesList = new ArrayList<>();
        for (int i = 2; i < allLines.size(); i++) {
            if (!allLines.get(i).isEmpty()) {
                String transDep = allLines.get(i).split("\\u001b")[0].trim();
                System.out.println("\t" + transDep);
                allDependenciesList.add(transDep);
            }
        }
        Set<String> allDependencies = new HashSet(allDependenciesList);

        System.out.println("Transitive dependencies");
        HashSet<String> allDependenciesSet = new HashSet(allDependenciesList);
        HashSet<String> transitiveDependencies = allDependenciesSet;
        transitiveDependencies.removeAll(directDependencies);

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

//        System.out.println("Unused and undeclared dependencies");

        // save results to file
        writeResults("/home/cesarsv/Documents/" + "results.csv",
                coordinates,
                usedDeclaredDependencies,
                usedUndeclaredDependencies,
                unusedDeclaredDependencies,
                directDependencies,
                transitiveDependencies,
                allDependencies);
    }

    /**
     * Download pom file from the Maven Central repository.
     *
     * @param artifactDir
     * @param groupId
     * @param artifactId
     * @param version
     * @throws IOException
     */
    private void downloadPom(String artifactDir, String groupId, String artifactId, String version) throws IOException {
        FileUtils.copyURLToFile(
                new URL("http://central.maven.org/maven2/" +
                        groupId.replace('.', '/') + "/" +
                        artifactId.replace('.', '/') + "/" +
                        version + "/" +
                        artifactId + "-" + version + ".pom"),
                new File(artifactDir + "pom.xml"));
    }

    /**
     * This method resolves all the direct and transitive dependencies of an artifact from Maven Central.
     *
     * @param pomPath
     * @param coordinates
     * @throws TestToolsException
     */
    private void resolveDependencies(String pomPath, String coordinates)
            throws TestToolsException {
        resolveDependencies(pomPath, coordinates, new Properties());
    }

    private void resolveDependencies(String pomPath, String coordinates, Properties properties) throws TestToolsException {
        File pom = new File(pomPath);
        List<String> goals = Arrays.asList("dependency:get", "-Dartifact=" + coordinates);
        File log = new File(pom.getParentFile(), "build.log");
        InvocationRequest request = buildTool.createBasicInvocationRequest(pom, properties, goals, log);
        request.setLocalRepositoryDirectory(localRepo);
        request.setPomFile(pom);
        buildTool.executeMaven(request);
    }

    /**
     * This method resolves all the direct and transitive dependencies of an artifact from Maven Central.
     *
     * @param pomPath
     * @param coordinates
     * @throws TestToolsException
     */
    private void copyDependencies(String pomPath, String coordinates, String outputDirectoryPath) throws TestToolsException {
        copyDependencies(pomPath, coordinates, outputDirectoryPath, new Properties());
    }

    private void copyDependencies(String pomPath, String coordinates, String outputDirectoryPath, Properties properties) throws TestToolsException {
        File pom = new File(pomPath);
        List<String> goals = Arrays.asList("dependency:copy-dependencies", "-DoutputDirectory=" + outputDirectoryPath, "-Dartifact=" + coordinates);
        File log = new File(pom.getParentFile(), "build.log");
        InvocationRequest request = buildTool.createBasicInvocationRequest(pom, properties, goals, log);
        request.setLocalRepositoryDirectory(localRepo);
        request.setPomFile(pom);
        buildTool.executeMaven(request);
    }

    /**
     * This method returns a list of transitive dependencies of an artifact from Maven Central.
     *
     * @param pomPath
     * @param coordinates
     * @throws TestToolsException
     */
    private void listAllDependencies(String pomPath, String coordinates, String dependenciesDir) throws TestToolsException {
        listAllDependencies(pomPath, coordinates, dependenciesDir, new Properties());
    }

    private void listAllDependencies(String pomPath, String coordinates, String dependenciesDir, Properties properties) throws TestToolsException {
        File pom = new File(pomPath);
        List<String> goals = Arrays.asList("dependency:list", "-DoutputFile=" + dependenciesDir, "-Dartifact=" + coordinates);
        File log = new File(pom.getParentFile(), "build.log");
        InvocationRequest request = buildTool.createBasicInvocationRequest(pom, properties, goals, log);
        request.setLocalRepositoryDirectory(localRepo);
        request.setPomFile(pom);
        buildTool.executeMaven(request);
    }

    /**
     * This method resolves all the direct and transitive dependencies of an artifact from Maven Central.
     *
     * @param pomPath
     * @param coordinates
     * @throws TestToolsException
     */
    private void copyArtifact(String pomPath, String coordinates, String outputDirectoryPath) throws TestToolsException {
        copyArtifact(pomPath, coordinates, outputDirectoryPath, new Properties());
    }

    private void copyArtifact(String pomPath, String coordinates, String outputDirectoryPath, Properties properties) throws TestToolsException {
        File pom = new File(pomPath);
        List<String> goals = Arrays.asList("dependency:copy", "-DoutputDirectory=" + outputDirectoryPath, "-Dartifact=" + coordinates);
        File log = new File(pom.getParentFile(), "build.log");
        InvocationRequest request = buildTool.createBasicInvocationRequest(pom, properties, goals, log);
        request.setLocalRepositoryDirectory(localRepo);
        request.setPomFile(pom);
        buildTool.executeMaven(request);
    }

    /**
     * This method decompresses a jar file into a given destination.
     *
     * @param destinationDir
     * @param jarPath
     * @throws IOException
     */
    private void decompressJarFile(String destinationDir, String jarPath) throws IOException {
        File file = new File(jarPath);
        JarFile jar = new JarFile(file);
        // fist get all directories,
        // then make those directory on the destination path
        for (Enumeration<JarEntry> enums = jar.entries(); enums.hasMoreElements(); ) {
            JarEntry entry = (JarEntry) enums.nextElement();
            String fileName = destinationDir + File.separator + entry.getName();
            File f = new File(fileName);
            if (fileName.endsWith("/")) {
                f.mkdirs();
            }
        }
        //now create all files
        for (Enumeration<JarEntry> enums = jar.entries(); enums.hasMoreElements(); ) {
            JarEntry entry = (JarEntry) enums.nextElement();
            String fileName = destinationDir + File.separator + entry.getName();
            File f = new File(fileName);
            if (!fileName.endsWith("/")) {
                InputStream is = jar.getInputStream(entry);
                FileOutputStream fos = new FileOutputStream(f);
                // write contents of 'is' to 'fos'
                while (is.available() > 0) {
                    fos.write(is.read());
                }
                fos.close();
                is.close();
            }
        }
    }

    /**
     * Writes the results to a file locally.
     *
     * @param filePath
     * @param artifact
     * @param usedDeclared
     * @param usedButUndeclared
     * @param unusedButDeclared
     * @throws IOException
     */
    private void writeResults(String filePath,
                              String artifact,
                              Set<Artifact> usedDeclared,
                              Set<Artifact> usedButUndeclared,
                              Set<Artifact> unusedButDeclared,
                              Set<String> directDependencies,
                              Set<String> transitiveDependencies,
                              Set<String> allDependencies) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(filePath));

        int max = allDependencies.size();

        List<Artifact> usedDeclaredList = new ArrayList(usedDeclared);
        List<Artifact> usedButUndeclaredList = new ArrayList(usedButUndeclared);
        List<Artifact> unusedButDeclaredList = new ArrayList(unusedButDeclared);
        List<Artifact> directDependenciesList = new ArrayList(directDependencies);
        List<Artifact> transitiveDependenciesList = new ArrayList(transitiveDependencies);
        List<Artifact> allDependenciesList = new ArrayList(allDependencies);

        // write header
        writer.write("Artifact,UsedDeclared,UsedButUndeclared,UnusetButDeclared,DirectDep,TransDep,AllDep" + "\n");

        for (int i = 0; i < max; i++) {
            // write artifact coordinates
            writer.write(artifact + ",");

            // write usedDeclared dependencies
            if (usedDeclaredList.size() > i) {
                writer.append(usedDeclaredList.get(i).toString() + ",");
            } else {
                writer.append("NA,");
            }

            // write usedButUndeclared dependencies
            if (usedButUndeclaredList.size() > i) {
                writer.append(usedButUndeclaredList.get(i).toString() + ",");
            } else {
                writer.append("NA,");
            }

            // write unusedButDeclared dependencies
            if (unusedButDeclaredList.size() > i) {
                writer.append(unusedButDeclaredList.get(i).toString() + ",");
            } else {
                writer.append("NA,");
            }

            // write direct dependencies
            if (directDependencies.size() > i) {
                writer.append(directDependenciesList.get(i) + ",");
            } else {
                writer.append("NA,");
            }

            // write transitive dependencies
            if (transitiveDependencies.size() > i) {
                writer.append(transitiveDependenciesList.get(i) + ",");
            } else {
                writer.append("NA,");
            }

            // write transitive dependencies
            if (allDependencies.size() > i) {
                writer.append(allDependenciesList.get(i) + "\n");
            } else {
                writer.append("NA," + "\n");
            }
        }
        writer.close();
    }
}
