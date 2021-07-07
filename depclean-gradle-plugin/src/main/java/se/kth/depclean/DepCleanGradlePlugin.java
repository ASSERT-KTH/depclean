package se.kth.depclean;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;

public class DepCleanGradlePlugin implements Plugin<Project> {

  String taskName = "debloat";

  @Override
  public void apply(@NotNull Project project) {

    // Creating extra configurations for the plugin to provide more flexibility.
    project.getExtensions().create("depclean", DepCleanGradlePluginExtension.class);

    // Creating the default task.
    createTask(project);
  }

  /**
   * Creating a task that performs the DepClean default action.
   *
   * @param project The Gradle project.
   */
  public void createTask(Project project) {
    DepCleanGradleTask task = project.getTasks().create(taskName, DepCleanGradleTask.class);
    task.setGroup("dependencyManagement");
    task.setDescription("Analyze the project byte-code and configure out the debloated dependencies.");
  }
}
