package se.kth.depclean;

import org.gradle.api.Plugin;
import org.gradle.api.Project;


public class GradlePluginExample implements Plugin<Project> {

    @Override
    public void apply(Project project) {

        GradlePluginExampleExtension extension = project.getExtensions().create("example",
                GradlePluginExampleExtension.class);

        project.task("example").doLast(task -> {

            try {
                extension.create(project);
            } catch (Exception e) {
                System.err.println("An exception occured during the generation: \n" + e.getMessage());
            }
        });
    }

}
