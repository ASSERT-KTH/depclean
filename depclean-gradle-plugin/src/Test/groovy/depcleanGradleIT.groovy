import org.gradle.testkit.runner.GradleRunner
import static org.gradle.testkit.runner.TaskOutcome.*
import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification
import spock.lang.Unroll

public class depcleanGradleIT extends Specification {

    @TempDir File testProject
    File settingsFile
    File buildFile

    def setup() {
        settingsFile = new File(testProject, 'settings.gradle')
        buildFile = new File(testProject, 'build.gradle')
    }

    @Unroll
    def "can execute hello world task with Gradle version #gradleVersion"() {
        given:
        buildFile << """
            plugins {
                id 'se.kth.castor.depclean-gradle-plugin'
            }
        """
        settingsFile << ""

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProject)
            .withArguments('debloat')
            .build()

        then:
        result.task(":debloat").outcome == SUCCESS
    }

//    File testProjectDir = new File("resources/all_dependencies_unused")
//    @Test
//    def "all_dependencies_unused"() {
//        given:
//        def project = ProjectBuilder.builder().build()
//
//        when:
//        project.plugins.apply("se.kth.castor.depclean-gradle-plugin")
//
//        then:
//        project.tasks.findByName("debloat") != null
//    }


}
