package se.kth.depclean.gradle.task;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import se.kth.depclean.core.analysis.ProjectDependencyAnalysis;
import se.kth.depclean.core.analysis.ProjectDependencyAnalyzer;
import se.kth.depclean.core.analysis.ProjectDependencyAnalyzerException;
import se.kth.depclean.gradle.DepCleanExtension;
import se.kth.depclean.gradle.analysis.GradleDependencyAnalyzer;
import se.kth.depclean.gradle.dt.InputType;
import se.kth.depclean.gradle.dt.Node;
import se.kth.depclean.gradle.dt.ParseException;
import se.kth.depclean.gradle.dt.Parser;
import se.kth.depclean.gradle.util.JarUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

public class DepCleanTask extends DefaultTask {

    @TaskAction
    public void depcleanAction() throws FileNotFoundException, UnsupportedEncodingException {

        DepCleanExtension extension = getProject().getExtensions().findByType(DepCleanExtension.class);
        if (extension == null) {
            extension = new DepCleanExtension();
        }

        String message = extension.getMessage();
        HelloWorld helloWorld = new HelloWorld(message);
        System.out.println(helloWorld.greet());

        System.out.println("-------------------------------------------------------");

        /* Initialize the Gradle connector */
        GradleConnector connector = new GradleConnector(getProject().getGradle().getGradleHomeDir().getAbsolutePath(), getProject().getProjectDir().getAbsolutePath());

        /* Copy direct dependencies locally */
        connector.executeTask("copyDependencies");

        /* Decompress dependencies */
        JarUtils.decompressJars(getProject().getBuildDir().getAbsolutePath() + "/dependencies");

        /* Generate a model */
        connector.executeTask("install");
        Model model = null;
        FileReader pomReader = null;
        MavenXpp3Reader mavenXpp3Reader = new MavenXpp3Reader();
        try {
            pomReader = new FileReader(getProject().getBuildDir().getAbsolutePath() + "/poms/pom-default.xml");
            model = mavenXpp3Reader.read(pomReader);
            model.setPomFile(new File(getProject().getBuildDir().getAbsolutePath() + "/poms/pom-default.xml"));
        } catch (Exception ex) {
        }
        MavenProject project = new MavenProject(model);
        Build build = new Build();
        build.setDirectory(getProject().getBuildDir().getAbsolutePath());
        project.setBuild(build);


        /* Analyze dependencies usage status */
        ProjectDependencyAnalysis projectDependencyAnalysis;
        try {
            ProjectDependencyAnalyzer dependencyAnalyzer = new GradleDependencyAnalyzer();
            projectDependencyAnalysis = dependencyAnalyzer.analyze(project);
        } catch (ProjectDependencyAnalyzerException e) {
            getLogger().error("Unable to analyze dependencies.");
            return;
        }

        printAnalysisResults(project, projectDependencyAnalysis);

        connector.executeTask("dependencyReportFile");

        String pathToDependencyTree = getProject().getBuildDir().getAbsolutePath() + "/dependencies/dependencies.txt";
        removeBlankLines(pathToDependencyTree);

        // parsing the dependency tree
        InputType type = InputType.TEXT;
        Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(pathToDependencyTree), StandardCharsets.UTF_8));
        Parser parser = type.newParser();
        try {
            Node tree = parser.parse(reader);
            System.out.println("direct dependencies: ");
            for (Node childNode : tree.getChildNodes()) {
                System.out.println("\t" + childNode.getArtifactCanonicalForm());
            }
        } catch (ParseException e) {
            System.out.println("Unable to parse the dependency tree file.");
        }

    }

    private void printAnalysisResults(MavenProject project, ProjectDependencyAnalysis projectDependencyAnalysis) {
        Set<Artifact> usedUndeclaredArtifacts = projectDependencyAnalysis.getUsedUndeclaredArtifacts();
        Set<Artifact> usedDeclaredArtifacts = projectDependencyAnalysis.getUsedDeclaredArtifacts();
        Set<Artifact> unusedDeclaredArtifacts = projectDependencyAnalysis.getUnusedDeclaredArtifacts();

        Set<Artifact> unusedUndeclaredArtifacts = project.getArtifacts();
        unusedUndeclaredArtifacts.removeAll(usedDeclaredArtifacts);
        unusedUndeclaredArtifacts.removeAll(usedUndeclaredArtifacts);
        unusedUndeclaredArtifacts.removeAll(unusedDeclaredArtifacts);

        /* Use artifacts coordinates for the report instead of the Artifact object */
        Set<String> usedDeclaredArtifactsCoordinates = new HashSet<>();
        usedDeclaredArtifacts.forEach(s -> usedDeclaredArtifactsCoordinates.add(s.getGroupId() + ":" + s.getArtifactId() + ":" + s.getVersion()));

        Set<String> usedUndeclaredArtifactsCoordinates = new HashSet<>();
        usedUndeclaredArtifacts.forEach(s -> usedUndeclaredArtifactsCoordinates.add(s.getGroupId() + ":" + s.getArtifactId() + ":" + s.getVersion()));

        Set<String> unusedDeclaredArtifactsCoordinates = new HashSet<>();
        unusedDeclaredArtifacts.forEach(s -> unusedDeclaredArtifactsCoordinates.add(s.getGroupId() + ":" + s.getArtifactId() + ":" + s.getVersion()));

        Set<String> unusedUndeclaredArtifactsCoordinates = new HashSet<>();
        unusedUndeclaredArtifacts.forEach(s -> unusedUndeclaredArtifactsCoordinates.add(s.getGroupId() + ":" + s.getArtifactId() + ":" + s.getVersion()));

        /* Printing the results to the console */
        System.out.println(" D E P C L E A N   A N A L Y S I S   R E S U L T S");
        System.out.println("-------------------------------------------------------");

        System.out.println("Used direct dependencies" + " [" + usedDeclaredArtifactsCoordinates.size() + "]" + ": ");
        usedDeclaredArtifactsCoordinates.stream().forEach(s -> System.out.println("\t" + s));

        System.out.println("Used transitive dependencies" + " [" + usedUndeclaredArtifactsCoordinates.size() + "]" + ": ");
        usedUndeclaredArtifactsCoordinates.stream().forEach(s -> System.out.println("\t" + s));

        System.out.println("Potentially unused direct dependencies" + " [" + unusedDeclaredArtifactsCoordinates.size() + "]" + ": ");
        unusedDeclaredArtifactsCoordinates.stream().forEach(s -> System.out.println("\t" + s));

        System.out.println("Potentially unused transitive dependencies" + " [" + unusedUndeclaredArtifactsCoordinates.size() + "]" + ": ");
        unusedUndeclaredArtifactsCoordinates.stream().forEach(s -> System.out.println("\t" + s));
    }

    private void removeBlankLines(String filePath) throws FileNotFoundException {
        Scanner file;
        PrintWriter writer;

        file = new Scanner(new File(filePath));
        writer = new PrintWriter(filePath + "_old");

        while (file.hasNext()) {
            String line = file.nextLine();
            if (!line.isEmpty() && !line.startsWith("\n") && (line.startsWith(" ") || line.startsWith("|") || line.startsWith("+") || line.startsWith("\\")
                    || line.startsWith("³") || line.startsWith("Ã") || line.startsWith("Ä") || line.startsWith("À"))) {
                writer.write(line);
                writer.write("\n");
            }
        }
        file.close();
        writer.close();

        File file1 = new File(filePath);
        File file2 = new File(filePath + "_old");

        file1.delete();
        file2.renameTo(file1);
    }

}
