package se.kth.depclean;

import com.soebes.itf.jupiter.extension.MavenGoal;
import com.soebes.itf.jupiter.extension.MavenJupiterExtension;
import com.soebes.itf.jupiter.extension.MavenTest;
import com.soebes.itf.jupiter.maven.MavenExecutionResult;
import org.junit.jupiter.api.Assertions;

@MavenJupiterExtension
public class DepCleanMojoIT {

  @MavenTest
  @MavenGoal("package")
//  @EnabledForMavenVersion(MavenVersion.M3_6_3)
  void first_very_simple(MavenExecutionResult result) {
    Assertions.assertTrue(result.isSuccesful());
//    assertThat(result).isSuccessful();
  }

}

