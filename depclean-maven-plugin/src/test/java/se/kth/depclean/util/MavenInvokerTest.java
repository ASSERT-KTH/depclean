package se.kth.depclean.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MavenInvoker}.
 */
@Slf4j
class MavenInvokerTest {

  static final File expectedTree = new File(
      "src/test/resources/MavenInvokerResources/basic_spring_maven_project/tree_expected.txt"
  );
  static final File producedTree = new File(
      "src/test/resources/MavenInvokerResources/basic_spring_maven_project/tree_produced.txt"
  );

  @Test
  @DisplayName("Test that the Maven dependency tree, then the dependency tree is obtained")
  void testRunCommandToGetDependencyTree() throws IOException, InterruptedException {
    File directory = new File("src/test/resources/MavenInvokerResources/basic_spring_maven_project");
    MavenInvoker.runCommand("mvn dependency:tree -DoutputFile=tree_produced.txt", directory);
    assertTrue(producedTree.exists());
    assertThat(producedTree).hasSameTextualContentAs(expectedTree);
  }

  @AfterAll
  public static void tearDown() throws IOException {
    if (producedTree.exists()) {
      FileUtils.forceDelete(producedTree);
    }
  }
}