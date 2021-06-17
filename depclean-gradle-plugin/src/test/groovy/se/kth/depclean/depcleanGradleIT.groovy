package se.kth.depclean

import org.apache.maven.BuildFailureException
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.gradle.testkit.runner.TaskOutcome.*
import spock.lang.Specification

class depcleanGradleIT extends Specification {

    File testProjectDir = new File("src/Test/resources/all_dependencies_unused")

    @Test
    def "debloatTaskIsFormed"() {
        given:
        def project = ProjectBuilder.builder().withProjectDir(testProjectDir).build()

        when:
        project.plugins.apply("se.kth.castor.depclean-gradle-plugin")

        then:
        project.tasks.findByName("debloat") != null
    }

    File emptyProjectFile = new File("src/Test/resources/empty_project")

    @Test
    def "pluginRunsOnEmptyProject"() {
        given:
        def project = ProjectBuilder.builder().withProjectDir(emptyProjectFile).build()

        when:
        project.plugins.apply("se.kth.castor.depclean-gradle-plugin")

        then:
        try {
            BuildResult result =  GradleRunner.create()
                .withProjectDir(emptyProjectFile)
                .withArguments("debloat")
                .buildAndFail()
        } catch (Exception e) {
            assertEquals(e, BuildFailureException)
        }
    }

    File allDependenciesUnused = new File("src/Test/resources/all_dependencies_unused")
    @Test
    def "all_dependencies_unused"() {
        given:
        def project = ProjectBuilder.builder().withProjectDir(allDependenciesUnused).build()

        when:
        project.plugins.apply("se.kth.castor.depclean-gradle-plugin")
        BuildResult result = GradleRunner.create()
                .withProjectDir(allDependenciesUnused)
                .withArguments("debloat")
                .build()

        then:
        assertEquals(SUCCESS, result.task(":debloat").getOutcome())
        String[] expectedOutput = {
            "USED DIRECT DEPENDENCIES [0]:"
            "USED INHERITED DEPENDENCIES [0]:"
            "USED TRANSITIVE DEPENDENCIES [0]:"
            "POTENTIALLY UNUSED DIRECT DEPENDENCIES [1]:"
            "\tcom.fasterxml.jackson.core:jackson-databind:2.12.2 (1 MB)"
            "POTENTIALLY UNUSED INHERITED DEPENDENCIES [0]:"
            "POTENTIALLY UNUSED TRANSITIVE DEPENDENCIES [2]:"
            "\tcom.fasterxml.jackson.core:jackson-annotations:2.12.2 (73 KB)"
            "\tcom.fasterxml.jackson.core:jackson-core:2.12.2 (356 KB)"
        }
        checkResult(result, expectedOutput)
    }

    File allDependenciesUsed = new File("src/Test/resources/all_dependencies_used")
    @Test
    def "all_dependencies_used"() {
        given:
        def project = ProjectBuilder.builder().withProjectDir(allDependenciesUsed).build()

        when:
        project.plugins.apply("se.kth.castor.depclean-gradle-plugin")
        BuildResult result = GradleRunner.create()
                .withProjectDir(allDependenciesUsed)
                .withArguments("debloat")
                .build()

        then:
        assertEquals(SUCCESS, result.task(":debloat").getOutcome())
        String[] expectedOutput = {
            "USED DIRECT DEPENDENCIES [1]:"
            "\tcom.fasterxml.jackson.core:jackson-databind:2.12.2 (1 MB)"
            "USED INHERITED DEPENDENCIES [0]:"
            "USED TRANSITIVE DEPENDENCIES [2]:"
            "\tcom.fasterxml.jackson.core:jackson-annotations:2.12.2 (73 KB)"
            "\tcom.fasterxml.jackson.core:jackson-core:2.12.2 (356 KB)"
            "POTENTIALLY UNUSED DIRECT DEPENDENCIES [0]:"
            "POTENTIALLY UNUSED INHERITED DEPENDENCIES [0]:"
            "POTENTIALLY UNUSED TRANSITIVE DEPENDENCIES [0]:"
        }
        checkResult(result, expectedOutput)
    }

    File processorUsed = new File("src/Test/resources/processor_used")
    @Test
    def "processor_used"() {
        given:
        def project = ProjectBuilder.builder().withProjectDir(processorUsed).build()

        when:
        project.plugins.apply("se.kth.castor.depclean-gradle-plugin")
        BuildResult result = GradleRunner.create()
                .withProjectDir(processorUsed)
                .withArguments("debloat")
                .build()

        then:
        assertEquals(SUCCESS, result.task(":debloat").getOutcome())
        String[] expectedOutput = {
            "USED DIRECT DEPENDENCIES [0]:"
            "USED INHERITED DEPENDENCIES [0]:"
            "USED TRANSITIVE DEPENDENCIES [1]:"
            "\tcom.fasterxml.jackson.core:jackson-core:2.12.2 (356 KB)"
            "POTENTIALLY UNUSED DIRECT DEPENDENCIES [2]:"
            "\tcom.fasterxml.jackson.core:jackson-databind:2.12.2 (1 MB)"
            "\torg.mapstruct:mapstruct-processor:1.4.2.Final (1 MB)"
            "POTENTIALLY UNUSED INHERITED DEPENDENCIES [0]:"
            "POTENTIALLY UNUSED TRANSITIVE DEPENDENCIES [1]:"
            "\tcom.fasterxml.jackson.core:jackson-annotations:2.12.2 (73 KB)"
        }
        checkResult(result, expectedOutput)
    }

    private static void checkResult(BuildResult result, String[] expectedOutput) {
        for (String expectedOutputLine : expectedOutput) {
            result.output.stripIndent().trim().contains(expectedOutputLine.stripIndent().trim())
        }
    }
}