package se.kth.depclean.gradle.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import se.kth.depclean.gradle.DepCleanExtension;
import se.kth.depclean.gradle.dt.InputType;
import se.kth.depclean.gradle.dt.Node;
import se.kth.depclean.gradle.dt.ParseException;
import se.kth.depclean.gradle.dt.Parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

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

        GradleConnector connector = new GradleConnector(getProject().getGradle().getGradleHomeDir().getAbsolutePath(), getProject().getProjectDir().getAbsolutePath());

        System.out.println("gradle task names: " + connector.getGradleTaskNames());
        // connector.getProjectDependencyNames().forEach(s -> System.out.println(s));

        connector.executeTask("copyDependencies");
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

        // Project project = getProject();
        // System.out.println("Get all dependencies: " + project.getConfigurations().detachedConfiguration().getAllDependencies());
        // System.out.println("Get resolved artifacts: " + project.getConfigurations().detachedConfiguration().getResolvedConfiguration().getResolvedArtifacts());
        //
        // System.out.println("******");
        // Configuration configuration = project.getConfigurations().getByName("runtimeClasspath");
        // for (File file : configuration) {
        //     project.getLogger().lifecycle("Found project dependency @ " + file.getAbsolutePath());
        // }
        //
        // Configuration configuration2 = project.getConfigurations().getByName("copyDependencies");
        //
        // for (Dependency dependency : configuration2.getDependencies()) {
        //     project.getLogger().lifecycle("Dependency @ " + dependency.getGroup() + ":" + dependency.getName());
        // }

    }

    /*
     *//* Copy direct dependencies locally *//*
        // TODO


        *//* Decompress dependencies *//*
        JarUtils.decompressJars(project.getBuild().getDirectory() + "/" + "dependency");

        *//* Analyze dependencies usage status *//*
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

        System.out.println("**************************************************");
        System.out.println("****************** RESULTS");
        System.out.println("**************************************************");

        System.out.println("Used direct dependencies" + " [" + usedDeclaredArtifacts.size() + "]" + ": ");
        usedDeclaredArtifacts.stream().forEach(s -> System.out.println("\t" + s));

        System.out.println("Used transitive dependencies" + " [" + usedUndeclaredArtifacts.size() + "]" + ": ");
        usedUndeclaredArtifacts.stream().forEach(s -> System.out.println("\t" + s));

        System.out.println("Potentially unused direct dependencies" + " [" + unusedDeclaredArtifacts.size() + "]" + ": ");
        unusedDeclaredArtifacts.stream().forEach(s -> System.out.println("\t" + s));

        System.out.println("Potentially unused transitive dependencies" + " [" + unusedUndeclaredArtifacts.size() + "]" + ": ");
        unusedUndeclaredArtifacts.stream().forEach(s -> System.out.println("\t" + s));
*/

    public static void removeBlankLines(String filePath) throws FileNotFoundException {
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
