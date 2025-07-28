package se.kth.depclean;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.jspecify.annotations.NonNull;

/**
 * This plugin checks the bytecode of a gradle project and configure out whether a dependency is
 * used in that project or not. After analysis, it also configures out the source of that dependency
 * i.e. whether <br>
 * <b>1)</b> It is declared directly in your build.gradle file or, <br>
 * <b>2)</b> Inherited from any direct dependency or, <br>
 * <b>3)</b> Just a transitive dependency.
 */
public class DepCleanGradlePlugin implements Plugin<Project> {

  @Override
  public void apply(@NonNull Project project) {

    final String depCleanConfigurationName = "depclean";

    // Creating extra configurations for the plugin to provide more flexibility.
    project.getExtensions().create(depCleanConfigurationName, DepCleanGradlePluginExtension.class);

    // Creating the default task.
    createTask(project);
  }

  /**
   * Creating a task that performs the DepClean default action.
   *
   * @param project The Gradle project.
   */
  public void createTask(final Project project) {
    final String depCleanTaskName = "debloat";
    DepCleanGradleTask task =
        project.getTasks().register(depCleanTaskName, DepCleanGradleTask.class).get();
    task.setGroup("dependency management");
    task.setDescription(
        "Analyze the project byte-code and configure out the debloated dependencies.");

    // Inject the project to avoid Task.project deprecation
    task.getTargetProject().set(project);
  }
}
