package se.kth.depclean;

import static com.soebes.itf.extension.assertj.MavenITAssertions.assertThat;

import com.soebes.itf.jupiter.extension.MavenGoal;
import com.soebes.itf.jupiter.extension.MavenJupiterExtension;
import com.soebes.itf.jupiter.extension.MavenTest;
import com.soebes.itf.jupiter.maven.MavenExecutionResult;
import org.junit.jupiter.api.DisplayName;

/**
 * This class executes integration tests against the DepCleanMojo.
 */
@MavenJupiterExtension
public class DepCleanMojoIT {

  @MavenTest
  @MavenGoal("package")
  @DisplayName("Test that DepClean runs in empty Maven project")
  void first_very_simple(MavenExecutionResult result) {
    assertThat(result).isSuccessful();
  }


}

