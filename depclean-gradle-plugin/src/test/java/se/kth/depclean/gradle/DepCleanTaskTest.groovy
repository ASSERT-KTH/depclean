package se.kth.depclean.gradle

import org.junit.Test
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.Project
import se.kth.depclean.gradle.task.DepCleanTask

import static org.junit.Assert.*

class DepCleanTaskTest {

    @Test
    public void should_be_able_to_add_task_to_project() {
        Project project = ProjectBuilder.builder().build()
        def task = project.task('depclean', type: DepCleanTask)
        assertTrue(task instanceof DepCleanTask)
    }
}
