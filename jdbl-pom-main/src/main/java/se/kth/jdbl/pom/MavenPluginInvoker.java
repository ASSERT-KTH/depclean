package se.kth.jdbl.pom;

import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.test.plugin.TestToolsException;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class MavenPluginInvoker {

    /**
     * This method resolves all the direct and transitive dependencies of an artifact from Maven Central.
     *
     * @param pomPath
     * @param coordinates
     * @throws TestToolsException
     */
    public void resolveDependencies(String pomPath, String coordinates)
            throws TestToolsException {
        resolveDependencies(pomPath, coordinates, new Properties());
    }

    private void resolveDependencies(String pomPath, String coordinates, Properties properties) throws TestToolsException {
        File pom = new File(pomPath);
        List<String> goals = Arrays.asList("dependency:get", "-Dartifact=" + coordinates);
        File log = new File(pom.getParentFile(), "build.log");
        executeRequest(properties, pom, goals, log);
    }

    /**
     * This method resolves all the direct and transitive dependencies of an artifact from Maven Central.
     *
     * @param pomPath
     * @param coordinates
     * @throws TestToolsException
     */
    public void copyDependencies(String pomPath, String coordinates, String outputDirectoryPath) throws TestToolsException {
        copyDependencies(pomPath, coordinates, outputDirectoryPath, new Properties());
    }

    private void copyDependencies(String pomPath, String coordinates, String outputDirectoryPath, Properties properties) throws TestToolsException {
        File pom = new File(pomPath);
        List<String> goals = Arrays.asList("dependency:copy-dependencies", "-DoutputDirectory=" + outputDirectoryPath, "-Dartifact=" + coordinates);
        File log = new File(pom.getParentFile(), "build.log");
        executeRequest(properties, pom, goals, log);
    }

    public void copyDependencyTree(String pomPath, String coordinates, String dependenciesDir) throws TestToolsException {
        copyDependencyTree(pomPath, coordinates, dependenciesDir, new Properties());
    }

    private void copyDependencyTree(String pomPath, String coordinates, String dependenciesDir, Properties properties) throws TestToolsException {
        File pom = new File(pomPath);
        List<String> goals = Arrays.asList("dependency:tree", "-Dverbose", "-DoutputFile=" + dependenciesDir, "-DoutputType=text", "-Dartifact=" + coordinates);
        File log = new File(pom.getParentFile(), "build.log");
        executeRequest(properties, pom, goals, log);
    }

    /**
     * This method resolves all the direct and transitive dependencies of an artifact from Maven Central.
     *
     * @param pomPath
     * @param coordinates
     * @throws TestToolsException
     */
    public void copyArtifact(String pomPath, String coordinates, String outputDirectoryPath) throws TestToolsException {
        copyArtifact(pomPath, coordinates, outputDirectoryPath, new Properties());
    }

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
}
