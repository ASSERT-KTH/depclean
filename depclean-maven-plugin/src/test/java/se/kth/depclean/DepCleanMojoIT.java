package se.kth.depclean;

import static com.soebes.itf.extension.assertj.MavenITAssertions.assertThat;

import com.soebes.itf.jupiter.extension.MavenJupiterExtension;
import com.soebes.itf.jupiter.extension.MavenTest;
import com.soebes.itf.jupiter.maven.MavenExecutionResult;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import se.kth.depclean.util.OsUtils;

/**
 * This class executes integration tests against the DepCleanMojo. The projects used for testing are in src/test/resources-its/se/kth/depclean/DepCleanMojoIT. The results of the DepClean executions
 * for each project are in target/maven-it/se/kth/depclean/DepCleanMojoIT.
 * <p>
 *
 * @see <a https://khmarbaise.github.io/maven-it-extension/itf-documentation/background/background.html#_assertions_in_maven_tests</a>
 */
@MavenJupiterExtension
@Slf4j
public class DepCleanMojoIT {

  @MavenTest
  void empty_project(MavenExecutionResult result) {
    log.trace("Test that DepClean runs in an empty Maven project");
    assertThat(result).isSuccessful(); // should pass
  }

  @MavenTest
  void used_java_record(MavenExecutionResult result) {
    log.trace("Test that DepClean identifies dependency used in Java record");
    assertThat(result).isSuccessful().out()
        .plain().contains(
            "-------------------------------------------------------",
            " D E P C L E A N   A N A L Y S I S   R E S U L T S",
            "-------------------------------------------------------",
            "USED DIRECT DEPENDENCIES [1]: ",
            "	commons-io:commons-io:2.11.0:compile (319 KB)",
            "USED TRANSITIVE DEPENDENCIES [0]: ",
            "USED INHERITED DIRECT DEPENDENCIES [0]: ",
            "USED INHERITED TRANSITIVE DEPENDENCIES [0]: ",
            "POTENTIALLY UNUSED DIRECT DEPENDENCIES [1]: ",
            "	org.apache.commons:commons-compress:1.21:compile (994 KB)",
            "POTENTIALLY UNUSED TRANSITIVE DEPENDENCIES [0]: ",
            "POTENTIALLY UNUSED INHERITED DIRECT DEPENDENCIES [0]: ",
            "POTENTIALLY UNUSED INHERITED TRANSITIVE DEPENDENCIES [0]: "
        );
  }

  @MavenTest
  void all_dependencies_unused(MavenExecutionResult result) {
    log.trace("Test that DepClean identifies all dependencies as unused");
    assertThat(result).isSuccessful().out()
        .plain().contains(
            "-------------------------------------------------------",
            " D E P C L E A N   A N A L Y S I S   R E S U L T S",
            "-------------------------------------------------------",
            "USED DIRECT DEPENDENCIES [0]: ",
            "USED TRANSITIVE DEPENDENCIES [0]: ",
            "USED INHERITED DIRECT DEPENDENCIES [0]: ",
            "USED INHERITED TRANSITIVE DEPENDENCIES [0]: ",
            "POTENTIALLY UNUSED DIRECT DEPENDENCIES [3]: ",
            "	com.google.guava:guava:31.0.1-jre:compile (2 MB)",
            "	com.fasterxml.jackson.core:jackson-databind:2.12.2:compile (1 MB)",
            "	commons-io:commons-io:2.11.0:compile (319 KB)",
            "POTENTIALLY UNUSED TRANSITIVE DEPENDENCIES [8]: ",
            "	com.fasterxml.jackson.core:jackson-core:2.12.2:compile (356 KB)",
            "	org.checkerframework:checker-qual:3.12.0:compile (203 KB)",
            "	com.fasterxml.jackson.core:jackson-annotations:2.12.2:compile (73 KB)",
            "	com.google.code.findbugs:jsr305:3.0.2:compile (19 KB)",
            "	com.google.errorprone:error_prone_annotations:2.7.1:compile (14 KB)",
            "	com.google.j2objc:j2objc-annotations:1.3:compile (8 KB)",
            "	com.google.guava:failureaccess:1.0.1:compile (4 KB)",
            "POTENTIALLY UNUSED INHERITED DIRECT DEPENDENCIES [0]: ",
            "POTENTIALLY UNUSED INHERITED TRANSITIVE DEPENDENCIES [0]: "
        );
  }

  @MavenTest
  void all_dependencies_used(MavenExecutionResult result) {
    log.trace("Test that DepClean identifies all dependencies as used");
    assertThat(result).isSuccessful().out()
        .plain().contains(
            "-------------------------------------------------------",
            " D E P C L E A N   A N A L Y S I S   R E S U L T S",
            "-------------------------------------------------------",
            "USED DIRECT DEPENDENCIES [5]: ",
            "	org.projectlombok:lombok:1.18.24:compile (1 MB)",
            "	org.apache.commons:commons-lang3:3.12.0:compile (573 KB)",
            "	commons-codec:commons-codec:1.15:compile (345 KB)",
            "	commons-io:commons-io:2.11.0:compile (319 KB)",
            "	org.kohsuke.metainf-services:metainf-services:1.8:compile (7 KB)",
            "USED TRANSITIVE DEPENDENCIES [0]: ",
            "USED INHERITED DIRECT DEPENDENCIES [0]: ",
            "USED INHERITED TRANSITIVE DEPENDENCIES [0]: ",
            "POTENTIALLY UNUSED DIRECT DEPENDENCIES [0]: ",
            "POTENTIALLY UNUSED TRANSITIVE DEPENDENCIES [0]: ",
            "POTENTIALLY UNUSED INHERITED DIRECT DEPENDENCIES [0]: ",
            "POTENTIALLY UNUSED INHERITED TRANSITIVE DEPENDENCIES [0]: "
        );
  }

  @MavenTest
  void used_indirectly(MavenExecutionResult result) {
    log.trace("Test that dependencies used indirectly (org.tukaani:xz is used indirectly)");
    assertThat(result).isSuccessful().out()
        .plain().contains(
            "-------------------------------------------------------",
            " D E P C L E A N   A N A L Y S I S   R E S U L T S",
            "-------------------------------------------------------",
            "USED DIRECT DEPENDENCIES [2]: ",
            "	org.apache.commons:commons-compress:1.21:compile (994 KB)",
            "	org.tukaani:xz:1.9:compile (113 KB)",
            "USED TRANSITIVE DEPENDENCIES [0]: ",
            "USED INHERITED DIRECT DEPENDENCIES [0]: ",
            "USED INHERITED TRANSITIVE DEPENDENCIES [0]: ",
            "POTENTIALLY UNUSED DIRECT DEPENDENCIES [0]: ",
            "POTENTIALLY UNUSED TRANSITIVE DEPENDENCIES [0]: ",
            "POTENTIALLY UNUSED INHERITED DIRECT DEPENDENCIES [0]: ",
            "POTENTIALLY UNUSED INHERITED TRANSITIVE DEPENDENCIES [0]: "
        );
  }

  @MavenTest
  void processor_used(MavenExecutionResult result) {
    log.trace("Test that DepClean runs in a Maven project with processors");
    assertThat(result).isSuccessful().out()
        .plain().contains(
            "-------------------------------------------------------",
            " D E P C L E A N   A N A L Y S I S   R E S U L T S",
            "-------------------------------------------------------",
            "USED DIRECT DEPENDENCIES [1]: ",
            "	org.mapstruct:mapstruct-processor:1.4.2.Final:provided (1 MB)",
            "USED TRANSITIVE DEPENDENCIES [1]: ",
            "	com.fasterxml.jackson.core:jackson-core:2.12.2:compile (356 KB)",
            "USED INHERITED DIRECT DEPENDENCIES [0]: ",
            "USED INHERITED TRANSITIVE DEPENDENCIES [0]: ",
            "POTENTIALLY UNUSED DIRECT DEPENDENCIES [1]: ",
            "	com.fasterxml.jackson.core:jackson-databind:2.12.2:compile (1 MB)",
            "POTENTIALLY UNUSED TRANSITIVE DEPENDENCIES [1]: ",
            "	com.fasterxml.jackson.core:jackson-annotations:2.12.2:compile (73 KB)",
            "POTENTIALLY UNUSED INHERITED DIRECT DEPENDENCIES [0]: ",
            "POTENTIALLY UNUSED INHERITED TRANSITIVE DEPENDENCIES [0]: "
        );
  }

  @MavenTest
  void json_should_be_correct(MavenExecutionResult result) throws IOException {
    if (OsUtils.isUnix()) {
      log.trace("Test that DepClean creates a proper depclean-results.json file");
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
  void debloated_pom_is_correct(MavenExecutionResult result) {
    log.trace("Test that DepClean creates a proper pom-debloated.xml file");
    String path = "target/maven-it/se/kth/depclean/DepCleanMojoIT/debloated_pom_is_correct/project/pom-debloated.xml";
    File generated_pom_debloated = new File(path);
    assertThat(result).isSuccessful()
        .out()
        .plain().contains(
            "[INFO] Starting debloating POM file...",
            "[INFO] Adding 1 used transitive dependency as direct dependency.",
            "[INFO] Removing 1 unused direct dependency.",
            "[INFO] Excluding 1 unused transitive dependency one-by-one.",
            "[INFO] Adding com.fasterxml.jackson.core:jackson-core:2.12.2:compile",
            "[INFO] Adding org.mapstruct:mapstruct-processor:1.4.2.Final:provided",
            "[INFO] Adding com.fasterxml.jackson.core:jackson-databind:2.12.2:compile",
            "[INFO] Excluding com.fasterxml.jackson.core:jackson-annotations from com.fasterxml.jackson.core:jackson-databind:2.12.2",
            "[INFO] POM debloated successfully",
            "[INFO] pom-debloated.xml file created in: " + generated_pom_debloated.getAbsolutePath());
    Assertions.assertTrue(generated_pom_debloated.exists());
    assertThat(generated_pom_debloated).
        hasSameTextualContentAs(new File(
            "src/test/resources/DepCleanMojoResources/pom-debloated.xml"));
  }

  @MavenTest
  @Disabled
  void unused_inherited_exists(MavenExecutionResult result) {
    log.trace("Test that DepClean detects unused inherited dependencies in a Maven project with a parent");
    assertThat(result).isSuccessful().out()
        .plain().contains(
            "-------------------------------------------------------",
            " D E P C L E A N   A N A L Y S I S   R E S U L T S",
            "-------------------------------------------------------",
            "USED DIRECT DEPENDENCIES [0]: ",
            "USED TRANSITIVE DEPENDENCIES [1]: ",
            "	com.fasterxml.jackson.core:jackson-core:2.12.2:compile (356 KB)",
            "USED INHERITED DIRECT DEPENDENCIES [0]: ",
            "USED INHERITED TRANSITIVE DEPENDENCIES [1]: ",
            "	org.junit.jupiter:junit-jupiter-api:5.9.1:test (202 KB)",
            "POTENTIALLY UNUSED DIRECT DEPENDENCIES [1]: ",
            "	com.fasterxml.jackson.core:jackson-databind:2.12.2:compile (1 MB)",
            "POTENTIALLY UNUSED TRANSITIVE DEPENDENCIES [1]: ",
            "	com.fasterxml.jackson.core:jackson-annotations:2.12.2:compile (73 KB)",
            "POTENTIALLY UNUSED INHERITED DIRECT DEPENDENCIES [2]: ",
            "	org.junit.vintage:junit-vintage-engine:5.9.1:test (65 KB)",
            "	org.junit.jupiter:junit-jupiter:5.9.1:test (6 KB)",
            "POTENTIALLY UNUSED INHERITED TRANSITIVE DEPENDENCIES [8]: ",
            "	org.junit.jupiter:junit-jupiter-params:5.9.1:test (565 KB)",
            "	junit:junit:4.13.2:test (375 KB)",
            "	org.junit.jupiter:junit-jupiter-engine:5.9.1:test (240 KB)",
            "	org.junit.platform:junit-platform-engine:1.9.1:test (183 KB)",
            "	org.junit.platform:junit-platform-commons:1.9.1:test (100 KB)",
            "	org.hamcrest:hamcrest-core:1.3:test (43 KB)",
            "	org.opentest4j:opentest4j:1.2.0:test (7 KB)",
            "	org.apiguardian:apiguardian-api:1.1.2:test (6 KB)"
        );
  }


  @MavenTest
  @Disabled
  void ignored_scopes(MavenExecutionResult result) {
    log.trace("Test that DepClean ignores dependencies (considers them as used) with the ignored scopes");
    assertThat(result).isSuccessful().out()
        .plain().contains(
            "-------------------------------------------------------",
            " D E P C L E A N   A N A L Y S I S   R E S U L T S",
            "-------------------------------------------------------",
            "USED DIRECT DEPENDENCIES [2]: ",
            "	com.fasterxml.jackson.core:jackson-core:2.12.2:compile (356 KB)",
            "	commons-io:commons-io:2.11.0:test (319 KB)",
            "USED TRANSITIVE DEPENDENCIES [2]: ",
            "	com.fasterxml.jackson.core:jackson-core:2.12.2:provided (356 KB)",
            "	com.fasterxml.jackson.core:jackson-annotations:2.12.2:provided (73 KB)",
            "USED INHERITED DIRECT DEPENDENCIES [0]: ",
            "USED INHERITED TRANSITIVE DEPENDENCIES [0]: ",
            "POTENTIALLY UNUSED DIRECT DEPENDENCIES [1]: ",
            "	com.google.guava:guava:31.0.1-jre:compile (2 MB)",
            "POTENTIALLY UNUSED TRANSITIVE DEPENDENCIES [6]: ",
            "	org.checkerframework:checker-qual:3.12.0:compile (203 KB)",
            "	com.google.code.findbugs:jsr305:3.0.2:compile (19 KB)",
            "	com.google.errorprone:error_prone_annotations:2.7.1:compile (14 KB)",
            "	com.google.j2objc:j2objc-annotations:1.3:compile (8 KB)",
            "	com.google.guava:failureaccess:1.0.1:compile (4 KB)",
            "	com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava:compile (2 KB)",
            "POTENTIALLY UNUSED INHERITED DIRECT DEPENDENCIES [0]: ",
            "POTENTIALLY UNUSED INHERITED TRANSITIVE DEPENDENCIES [0]: "
        );
  }
}

