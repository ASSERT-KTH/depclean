package se.kth.depclean;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;

public class DepCleanGradlePlugin implements Plugin<Project> {

  String taskName = "debloat";

  @Override
  public void apply(@NotNull Project project) {

    project.getExtensions().create("depclean", DepCleanGradlePluginExtension.class);
    createTask(project);
  }

  public void createTask(Project project) {
    DepCleanGradleTask task = project.getTasks().create(taskName, DepCleanGradleTask.class);
    task.setGroup("group");
    task.setDescription("Print outcome.");
  }
}
