package se.kth.depclean;

import static com.soebes.itf.extension.assertj.MavenITAssertions.assertThat;

import com.soebes.itf.jupiter.extension.MavenJupiterExtension;
import com.soebes.itf.jupiter.extension.MavenTest;
import com.soebes.itf.jupiter.maven.MavenExecutionResult;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import se.kth.depclean.util.OsUtils;

/**
 * This class executes integration tests against the DepCleanMojo. The projects used for testing are in
 * src/test/resources-its/se/kth/depclean/DepCleanMojoIT. The results of the DepClean executions for each project are in
 * target/maven-it/se/kth/depclean/DepCleanMojoIT.
 * <p>
 *
 * @see <a https://khmarbaise.github.io/maven-it-extension/itf-documentation/background/background.html#_assertions_in_maven_tests</a>
 */
@MavenJupiterExtension
public class DepCleanMojoIT {

  @MavenTest
  @DisplayName("Test that DepClean runs in an empty Maven project")
  void empty_project(MavenExecutionResult result) {
    assertThat(result).isFailure(); // should pass
  }

  @MavenTest
  @DisplayName("Test that DepClean runs in a Maven project with processors")
  void processor_used(MavenExecutionResult result) {
    assertThat(result).isSuccessful().out()
        .plain().contains(
        "-------------------------------------------------------",
        " D E P C L E A N   A N A L Y S I S   R E S U L T S",
        "-------------------------------------------------------",
        "USED DIRECT DEPENDENCIES [1]: ",
        "	org.mapstruct:mapstruct-processor:1.4.2.Final:provided (1 MB)",
        "USED INHERITED DEPENDENCIES [0]: ",
        "USED TRANSITIVE DEPENDENCIES [1]: ",
        "	com.fasterxml.jackson.core:jackson-core:2.12.2:compile (356 KB)",
        "POTENTIALLY UNUSED DIRECT DEPENDENCIES [1]: ",
        "	com.fasterxml.jackson.core:jackson-databind:2.12.2:compile (1 MB)",
        "POTENTIALLY UNUSED INHERITED DEPENDENCIES [0]: ",
        "POTENTIALLY UNUSED TRANSITIVE DEPENDENCIES [1]: ",
        "	com.fasterxml.jackson.core:jackson-annotations:2.12.2:compile (73 KB)"
    );
  }

  @MavenTest
  @DisplayName("Test that DepClean creates a proper depclean-results.json file")
  void json_should_be_correct(MavenExecutionResult result) throws IOException {
    if (OsUtils.isUnix()) {
      File expectedJsonFile = new File("src/test/resources/DepCleanMojoResources/depclean-results.json");
      String expectedJsonContent = FileUtils.readFileToString(expectedJsonFile, Charset.defaultCharset());
      assertThat(result).isSuccessful()
          .project()
          .hasTarget()
          .withFile("depclean-results.json")
          .hasContent(expectedJsonContent);
    }
  }

  @MavenTest
  @DisplayName("Test that DepClean creates a proper pom-debloated.xml file")
  void debloated_pom_is_correct(MavenExecutionResult result) throws IOException {
    String path = "target/maven-it/se/kth/depclean/DepCleanMojoIT/debloated_pom_is_correct/project/pom-debloated.xml";
    File generated_pom_debloated = new File(path);
    assertThat(result).isSuccessful()
        .out()
        .plain().contains(
        "[INFO] Starting debloating POM",
        "[INFO] Adding 1 used transitive dependencies as direct dependencies.",
        "[INFO] Removing 1 unused direct dependencies.",
        "[INFO] Excluding 1 unused transitive dependencies one-by-one.",
        "[INFO] POM debloated successfully",
        "[INFO] pom-debloated.xml file created in: " + generated_pom_debloated.getAbsolutePath());
    Assertions.assertTrue(generated_pom_debloated.exists());
    assertThat(generated_pom_debloated).
        hasSameTextualContentAs(new File(
            "src/test/resources/DepCleanMojoResources/pom-debloated.xml"));
  }
}

