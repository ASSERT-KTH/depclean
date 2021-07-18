package se.kth.depclean

import org.gradle.api.tasks.TaskExecutionException
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.gradle.testkit.runner.TaskOutcome.*
import spock.lang.Specification

import static org.junit.jupiter.api.Assertions.assertTrue

class DepCleanGradleFT extends Specification {

  File emptyProjectFile = new File("src/Test/resources-fts/empty_project")
  @Test
  @DisplayName("Test that depclean gradle plugin runs on an empty project.")
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
      assertEquals(e, TaskExecutionException)
    }
  }

  File allDependenciesUnused = new File("src/Test/resources-fts/all_dependencies_unused")
  @Test
  @DisplayName("Test that depclean gradle plugin runs on a project which has only unused dependencies.")
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
        "\tcom.fasterxml.jackson.core:jackson-databind:2.12.2:compile (1 MB)"
        "POTENTIALLY UNUSED INHERITED DEPENDENCIES [0]:"
        "POTENTIALLY UNUSED TRANSITIVE DEPENDENCIES [2]:"
        "\tcom.fasterxml.jackson.core:jackson-core:2.12.2:compile (356 KB)"
        "\tcom.fasterxml.jackson.core:jackson-annotations:2.12.2:compile (73 KB)"
    }
    for (String expectedOutputLine : expectedOutput) {
      result.output.stripIndent().trim().contains(expectedOutputLine.stripIndent().trim())
    }
  }

  File allDependenciesUsed = new File("src/Test/resources-fts/all_dependencies_used")
  @Test
  @DisplayName("Test that depclean gradle plugin runs on a project which has only used dependencies.")
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
      "\tcom.fasterxml.jackson.core:jackson-databind:2.12.2:compile (1 MB)"
      "USED INHERITED DEPENDENCIES [0]:"
      "USED TRANSITIVE DEPENDENCIES [2]:"
      "\tcom.fasterxml.jackson.core:jackson-annotations:2.12.2:compile (73 KB)"
      "\tcom.fasterxml.jackson.core:jackson-core:2.12.2:compile (356 KB)"
      "POTENTIALLY UNUSED DIRECT DEPENDENCIES [0]:"
      "POTENTIALLY UNUSED INHERITED DEPENDENCIES [0]:"
      "POTENTIALLY UNUSED TRANSITIVE DEPENDENCIES [0]:"
    }
    for (String expectedOutputLine : expectedOutput) {
      result.output.stripIndent().trim().contains(expectedOutputLine.stripIndent().trim())
    }
  }

  File debloatedDependenciesIsCorrect =
          new File("src/test/resources-fts/debloated_dependencies.gradle_is_correct")
  String path = "src/test/resources-fts/debloated_dependencies.gradle_is_correct/" +
          "debloated-dependencies.gradle";
  File generatedDebloatedDependenciesDotGradle = new File(path);
  @Test
  @DisplayName("Test that the depclean creates a proper debloated-dependencies.gradle file.")
  def "debloated_dependencies.gradle_is_correct"() {
    given:
    def project = ProjectBuilder.builder().withProjectDir(debloatedDependenciesIsCorrect).build()

    when:
    project.plugins.apply("se.kth.castor.depclean-gradle-plugin")
    BuildResult result = GradleRunner.create()
            .withProjectDir(debloatedDependenciesIsCorrect)
            .withArguments("debloat")
            .build()

    then:
    assertEquals(SUCCESS, result.task(":debloat").getOutcome())
    String[] expectedOutput = {
      "Starting debloating dependencies"
      "Adding 0 used direct dependencies"
      "Adding 1 used transitive dependencies as direct dependencies."
      "Excluding 1 unused transitive dependencies one-by-one."
      "Dependencies debloated successfully"
      "debloated-dependencies.gradle file created in: " +
              generatedDebloatedDependenciesDotGradle.getAbsolutePath()
    }
    for (String expectedOutputLine : expectedOutput) {
      result.output.stripIndent().trim().contains(expectedOutputLine.stripIndent().trim())
    }
    assertTrue(generatedDebloatedDependenciesDotGradle.exists())
  }

}