package se.kth.depclean;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;

/**
 * Task that configures out the bloated dependencies.
 */
public class DepCleanGradleTask extends DefaultTask {

  /**
   * Action that analyzes the project dependencies and show the results.
   */
  @TaskAction
  public void analyzeProject() {

    getProject().getLogger().lifecycle("Starting DepClean dependency analysis");

    // Applying the only default action on the project and it's sub-projects (if any).
    Action<Project> defaultAction = new DepCleanGradleAction();
    getProject().allprojects(defaultAction);

  }

}
