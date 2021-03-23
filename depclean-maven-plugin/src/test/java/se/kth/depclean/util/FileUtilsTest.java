package se.kth.depclean.util;

import java.io.File;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FileUtils}.
 */
@Slf4j
class FileUtilsTest {

  // The directories used for testing
  static final File originalDir = new File("src/test/resources/JarUtilsResources");
  static final File copyDir = new File("src/test/resources/JarUtilsResources_copy");

  @BeforeEach
  void setUp() {
    try {
      // make a copy of the directory containing the JAR files
      org.apache.commons.io.FileUtils.copyDirectory(
          originalDir,
          copyDir
      );
    } catch (IOException e) {
      log.error("Error copying the directory: src/test/resources/JarUtilsResources");
    }
  }

  @Test
  @DisplayName("Test that the directory is completely deleted")
  void whenDirectoryIs() throws IOException {
    if (copyDir.exists()) {
      FileUtils.deleteDirectory(copyDir);
    }
    Assertions.assertFalse(copyDir.exists());
  }

}