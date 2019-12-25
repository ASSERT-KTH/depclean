package se.kth.depclean.gradle


import org.junit.Test
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.Project
import se.kth.depclean.gradle.task.DepCleanTask

import static org.junit.Assert.*

class DepCleanPluginTest {

    @Test
    public void demo_plugin_should_add_task_to_project() {
        Project project = ProjectBuilder.builder().build()
        project.getPlugins().apply 'se.thinkcode.demo.plugin'

        assertTrue(project.tasks.demo instanceof DepCleanTask)
    }
}
