package se.kth.depclean;

import static com.soebes.itf.extension.assertj.MavenITAssertions.assertThat;

import com.soebes.itf.jupiter.extension.MavenGoal;
import com.soebes.itf.jupiter.extension.MavenJupiterExtension;
import com.soebes.itf.jupiter.extension.MavenTest;
import com.soebes.itf.jupiter.maven.MavenExecutionResult;
import org.junit.jupiter.api.DisplayName;

/**
 * This class executes integration tests against the DepCleanMojo. The projects used for testing are in
 * src/test/resources-its/se/kth/depclean/DepCleanMojoIT. The results of the DepClean executions for each project are in
 * target/maven-it/se/kth/depclean/DepCleanMojoIT.
 * <p>
 * @see <a https://khmarbaise.github.io/maven-it-extension/itf-documentation/background/background.html#_assertions_in_maven_tests</a>
 */
@MavenJupiterExtension
public class DepCleanMojoIT {

  @MavenTest
  @MavenGoal("package")
  @DisplayName("Test that DepClean runs in an empty Maven project")
  void empty_project(MavenExecutionResult result) {
    assertThat(result).isFailure(); // should pass
  }

}

