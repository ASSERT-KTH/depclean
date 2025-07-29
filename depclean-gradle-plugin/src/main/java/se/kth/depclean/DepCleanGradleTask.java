package se.kth.depclean;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

/** Task that configures out the bloated dependencies. */
public abstract class DepCleanGradleTask extends DefaultTask {

  /** The project instance injected during configuration to avoid Task.project deprecation. */
  @Internal
  public abstract Property<Project> getTargetProject();

  /** Action that analyzes the project dependencies and show the results. */
  @TaskAction
  public void analyzeProject() {

    Project project = getTargetProject().get();
    project.getLogger().lifecycle("Starting DepClean dependency analysis");

    // Applying the action on the target project only
    DepCleanGradleAction defaultAction = new DepCleanGradleAction();
    defaultAction.execute(project);

    // For multi-project builds, also analyze subprojects
    for (Project subproject : project.getSubprojects()) {
      project.getLogger().lifecycle("Analyzing subproject: " + subproject.getName());
      defaultAction.execute(subproject);
    }
  }
}
