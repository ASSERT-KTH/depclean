package se.kth.depclean.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import se.kth.depclean.gradle.task.DepCleanTask;

/**
 * Registers the plugin's tasks.
 */
public class DepCleanPlugin implements Plugin<Project> {

    public void apply(Project project) {
        project.getExtensions().create("depcleanSettings", DepCleanExtension.class);
        project.getTasks().create("depclean", DepCleanTask.class);
    }
}

