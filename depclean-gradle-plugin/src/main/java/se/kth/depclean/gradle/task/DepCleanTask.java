package se.kth.depclean.gradle.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import se.kth.depclean.gradle.DepCleanExtension;

public class DepCleanTask extends DefaultTask {

    @TaskAction
    public void depcleanAction() {

        DepCleanExtension extension = getProject().getExtensions().findByType(DepCleanExtension.class);
        if (extension == null) {
            extension = new DepCleanExtension();
        }

        String message = extension.getMessage();
        HelloWorld helloWorld = new HelloWorld(message);
        System.out.println(helloWorld.greet());

        GradleConnector connector = new GradleConnector(getProject().getGradle().getGradleHomeDir().getAbsolutePath(), getProject().getProjectDir().getAbsolutePath());

        System.out.println("gradle task names: " + connector.getGradleTaskNames());
        connector.getProjectDependencyNames().forEach(s -> System.out.println(s));

        connector.executeTask("copyDependencies");

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
}
