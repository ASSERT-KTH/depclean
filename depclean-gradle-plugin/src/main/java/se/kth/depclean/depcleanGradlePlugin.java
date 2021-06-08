package se.kth.depclean;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class depcleanGradlePlugin implements Plugin<Project> {

    String taskName = "debloat";

    @Override
    public void apply(Project project) {
        depcleanGradleTask task = createTask(project);
    }

    public depcleanGradleTask createTask(Project project) {
        depcleanGradleTask task = project.getTasks().create(taskName, depcleanGradleTask.class);
        task.setGroup("group");
        task.setDescription("Print outcome.");
        return task;
    }
}
