package se.kth.depclean

import org.apache.maven.BuildFailureException
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import spock.lang.Specification
import static org.junit.jupiter.api.Assertions.assertEquals

class DepCleanGradleFT extends Specification {

  File emptyProjectFile = new File("src/test/resources-fts/empty_project")
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
      assertEquals(e, BuildFailureException)
    }
  }
}