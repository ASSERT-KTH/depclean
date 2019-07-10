package se.kth.jdbl.util;

import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.test.plugin.TestToolsException;
import se.kth.jdbl.App;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class MavenPluginInvoker {

    //--------------------------/
    //----- PUBLIC METHODS -----/
    //--------------------------/

    /**
     * This method resolves all the direct and transitive dependencies of an artifact from Maven Central.
     *
     * @param pomPath     path to the pom of the artifact
     * @param coordinates artifact's coordinates
     */
    public void resolveDependencies(String pomPath, String coordinates)
            throws TestToolsException {
        resolveDependencies(pomPath, coordinates, new Properties());
    }

    /**
     * This method copies all the direct and transitive dependencies of an artifact from Maven Central.
     *
     * @param pomPath     path to the pom of the artifact
     * @param coordinates artifact's coordinates
     */
    public void copyDependencies(String pomPath, String coordinates, String outputDirectoryPath) throws TestToolsException {
        copyDependencies(pomPath, coordinates, outputDirectoryPath, new Properties());
    }

    public void copyDependencyTree(String pomPath, String coordinates, String dependenciesDir) throws TestToolsException {
        copyDependencyTree(pomPath, coordinates, dependenciesDir, new Properties());
    }

    /**
     * This method resolves all the direct and transitive dependencies of an artifact from Maven Central.
     *
     * @param pomPath     path to the pom of the artifact
     * @param coordinates artifact's coordinates
     */
    public void copyArtifact(String pomPath, String coordinates, String outputDirectoryPath) throws TestToolsException {
        copyArtifact(pomPath, coordinates, outputDirectoryPath, new Properties());
    }

    //--------------------------/
    //---- PRIVATE METHODS -----/
    //--------------------------/

    private void copyArtifact(String pomPath, String coordinates, String outputDirectoryPath, Properties properties) throws TestToolsException {
        File pom = new File(pomPath);
        List<String> goals = Arrays.asList("dependency:copy", "-DoutputDirectory=" + outputDirectoryPath, "-Dartifact=" + coordinates);
        File log = new File(pom.getParentFile(), "build.log");
        executeRequest(properties, pom, goals, log);
    }

    private void executeRequest(Properties properties, File pom, List<String> goals, File log) throws TestToolsException {
        InvocationRequest request = App.getBuildTool().createBasicInvocationRequest(pom, properties, goals, log);
        request.setLocalRepositoryDirectory(App.getLocalRepo());
        request.setPomFile(pom);
        App.getBuildTool().executeMaven(request);
    }

    private void copyDependencyTree(String pomPath, String coordinates, String dependenciesDir, Properties properties) throws TestToolsException {
        File pom = new File(pomPath);
        List<String> goals = Arrays.asList("dependency:tree", "-Dverbose", "-DoutputFile=" + dependenciesDir, "-DoutputType=text", "-Dartifact=" + coordinates);
        File log = new File(pom.getParentFile(), "build.log");
        executeRequest(properties, pom, goals, log);
    }

    private void copyDependencies(String pomPath, String coordinates, String outputDirectoryPath, Properties properties) throws TestToolsException {
        File pom = new File(pomPath);
        List<String> goals = Arrays.asList("dependency:copy-dependencies", "-DaddParentPoms=true", "-DuseRepositoryLayout=true", "-DoutputDirectory=" + outputDirectoryPath, "-Dartifact=" + coordinates);
        File log = new File(pom.getParentFile(), "build.log");
        executeRequest(properties, pom, goals, log);
    }

    private void resolveDependencies(String pomPath, String coordinates, Properties properties) throws TestToolsException {
        File pom = new File(pomPath);
        List<String> goals = Arrays.asList("dependency:get", "-Dartifact=" + coordinates);
        File log = new File(pom.getParentFile(), "build.log");
        executeRequest(properties, pom, goals, log);
    }
}
