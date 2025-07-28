package se.kth.depclean

import org.apache.maven.BuildFailureException
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import se.kth.depclean.util.FileUtils
import spock.lang.Specification

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

class DepCleanGradleFT extends Specification {

    File emptyProjectFile = new File("src/test/resources-fts/empty_project")

    def "Test that the DepClean gradle plugin runs on an empty project"() {
        given:
        def project = ProjectBuilder.builder().withProjectDir(emptyProjectFile).build()

        when:
        project.plugins.apply("se.kth.castor.depclean-gradle-plugin")

        then:
        try {
            BuildResult result = GradleRunner.create()
                    .withProjectDir(emptyProjectFile)
                    .withArguments("debloat")
                    .withDebug(true)
                    .buildAndFail()
        } catch (Exception e) {
            assert e.class == BuildFailureException
        }
    }

    String projectPath1 = "src/test/resources-fts/all_dependencies_unused"
    File allDependenciesUnused = new File(projectPath1)
    File originalOutputFile1 = new File(projectPath1 + "/originalOutputFile.txt")
    File expectedOutputFile1 = new File(projectPath1 + "/expectedOutputFile.txt")

    def "Test that depclean gradle plugin runs on a project which has only unused dependencies"() {
        given:
        def project = ProjectBuilder.builder().withProjectDir(allDependenciesUnused).build()

        when:
        project.plugins.apply("se.kth.castor.depclean-gradle-plugin")
        BuildResult buildResult = createRunner(allDependenciesUnused, "build")
        BuildResult debloatResult = createRunner(allDependenciesUnused, "debloat")

        then:
        assert checkTaskOutcome(buildResult.task(":build").getOutcome())
        assert checkTaskOutcome(debloatResult.task(":debloat").getOutcome())

        originalOutputFile1.write(debloatResult.getOutput())
        assert compareOutputs(expectedOutputFile1, originalOutputFile1)
        FileUtils.forceDelete(new File(projectPath1 + "/build"))
    }

    String projectPath2 = "src/test/resources-fts/all_dependencies_used"
    File allDependenciesUsed = new File(projectPath2)
    File originalOutputFile2 = new File(projectPath2 + "/originalOutputFile.txt")
    File expectedOutputFile2 = new File(projectPath2 + "/expectedOutputFile.txt")

    def "Test that depclean gradle plugin runs on a project which has only used dependencies"() {
        given:
        def project = ProjectBuilder.builder().withProjectDir(allDependenciesUsed).build()

        when:
        project.plugins.apply("se.kth.castor.depclean-gradle-plugin")
        BuildResult buildResult = createRunner(allDependenciesUsed, "build")
        BuildResult debloatResult = createRunner(allDependenciesUsed, "debloat")

        then:
        assert checkTaskOutcome(buildResult.task(":build").getOutcome())
        assert checkTaskOutcome(debloatResult.task(":debloat").getOutcome())

        originalOutputFile2.write(debloatResult.getOutput())
        assert compareOutputs(expectedOutputFile2, originalOutputFile2)
        FileUtils.forceDelete(new File(projectPath2 + "/build"))
    }

    String projectPath3 = "src/test/resources-fts/debloated_dependencies.gradle_is_correct"
    File debloatedDependenciesIsCorrect = new File(projectPath3)
    File generatedDebloatedDependenciesDotGradle = new File(projectPath3 + "/debloated-dependencies.gradle");

    def "Test that the DepClean creates a proper debloated-dependencies.gradle file"() {
        given:
        def project = ProjectBuilder.builder().withProjectDir(debloatedDependenciesIsCorrect).build()

        when:
        project.plugins.apply("se.kth.castor.depclean-gradle-plugin")
        BuildResult buildResult = createRunner(debloatedDependenciesIsCorrect, "build")
        BuildResult debloatResult = createRunner(debloatedDependenciesIsCorrect, "debloat")

        then:
        assert checkTaskOutcome(buildResult.task(":build").getOutcome())
        assert checkTaskOutcome(debloatResult.task(":debloat").getOutcome())

        assert generatedDebloatedDependenciesDotGradle.exists()
        FileUtils.forceDelete(new File(projectPath3 + "/build"))
    }

    String projectPath4 = "src/test/resources-fts/json_should_be_correct"
    File json_should_be_correct = new File(projectPath4)
    File generatedResultDotJson = new File(projectPath4 + "/build/depclean-results.json");

    def "Test that the depclean creates a proper results.json file"() {
        given:
        def project = ProjectBuilder.builder().withProjectDir(json_should_be_correct).build()

        when:
        project.plugins.apply("se.kth.castor.depclean-gradle-plugin")
        BuildResult buildResult = createRunner(json_should_be_correct, "build")
        BuildResult debloatResult = createRunner(json_should_be_correct, "debloat")

        then:
        assert checkTaskOutcome(buildResult.task(":build").getOutcome())
        assert checkTaskOutcome(debloatResult.task(":debloat").getOutcome())

        assert generatedResultDotJson.exists()
    }

    private static BuildResult createRunner(File project, String argument) {
        BuildResult result = GradleRunner.create()
                .withProjectDir(project)
                .withArguments(argument)
                .withDebug(true)
                .build()
        return result
    }

    private static boolean compareOutputs(File expectedOutputFile, File originalOutputFile) {

        FileReader fileReader1 = new FileReader(expectedOutputFile)
        FileReader fileReader2 = new FileReader(originalOutputFile)
        BufferedReader reader1 = new BufferedReader(fileReader1)
        BufferedReader reader2 = new BufferedReader(fileReader2)

        String line1, line2
        while (true) {
            // Continue while there are equal lines
            line1 = reader1.readLine()
            line2 = reader2.readLine()

            if (line1 == null) {
                // End of file 1
                return 1
            }
            if (!line1.trim().equalsIgnoreCase(line2.trim())) {
                // Different lines, or end of file 2
                return 0
            }
        }
    }

    private static boolean checkTaskOutcome(TaskOutcome outcome) {
        return outcome == SUCCESS || outcome == UP_TO_DATE;
    }
}