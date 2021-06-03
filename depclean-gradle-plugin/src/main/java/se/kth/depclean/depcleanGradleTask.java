package se.kth.depclean;

import java.util.Locale;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class depcleanGradleTask extends DefaultTask {

    public static final Logger log = LoggerFactory.getLogger(depcleanGradleTask.class);

    @TaskAction
    public void printDependencies() {
        log.info("Debloating starts...".toUpperCase(Locale.ROOT));

        Action<Project> defaultAction = new depcleanGradleAction();
        getProject().allprojects(defaultAction);

        log.info("Debloating ends.".toUpperCase(Locale.ROOT));
    }

}
