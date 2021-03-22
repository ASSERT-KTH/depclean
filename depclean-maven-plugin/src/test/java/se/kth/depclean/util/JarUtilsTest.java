package se.kth.depclean.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

@Slf4j
class JarUtilsTest {

  // The directories used for testing
  static final File originalDir = new File("src/test/resources/JarUtilsResources");
  static final File copyDir = new File("src/test/resources/JarUtilsResources_copy");

  // The JAR files used for testing
  static final File jcabiSSHJar = new File("src/test/resources/JarUtilsResources_copy/jcabi-ssh-1.6.jar");
  static final File jcabiXMLJar = new File("src/test/resources/JarUtilsResources_copy/jcabi-xml-0.22.2.jar");

  @BeforeEach
  void setUp() {
    try {
      // make a copy of the directory containing the JAR files
      FileUtils.copyDirectory(
          originalDir,
          copyDir
      );
    } catch (IOException e) {
      log.error("Error copying the directory: src/test/resources/JarUtilsResources");
    }
  }

  @AfterEach
  void afterEach() {
    try {
      FileUtils.deleteDirectory(copyDir);
    } catch (IOException e) {
      log.error("Error deleting the directory: src/test/resources/JarUtilsResources_copy");
    }
  }

  @Test
  @Order(1)
  @DisplayName("Test that the JAR files are removed after being decompressed")
  void whenDecompressJarFiles_thenJarFilesAreRemoved() throws RuntimeException, IOException {
    if (jcabiSSHJar.exists() && jcabiXMLJar.exists()) {
      decompress();
      assertFalse(FileUtils.directoryContains(copyDir, jcabiSSHJar));
      assertFalse(FileUtils.directoryContains(copyDir, jcabiSSHJar));
    }
  }

  @Test
  @Order(2)
  @DisplayName("Test that when decompressing JAR files other (decompressed) files are created")
  void whenDecompressJarFiles_thenOtherFilesAreCreated() throws RuntimeException {
    // Assert that POM files in JARs are decompressed
    if (jcabiSSHJar.exists() && jcabiXMLJar.exists()) {
      decompress();
      assertTrue(
          new File("src/test/resources/JarUtilsResources_copy/META-INF/maven/com.jcabi/jcabi-ssh/pom.xml").exists()
      );
      assertTrue(
          new File("src/test/resources/JarUtilsResources_copy/META-INF/maven/com.jcabi/jcabi-xml/pom.xml").exists());
    }
    // Assert that CLASS files in JARs are decompressed
    if (jcabiSSHJar.exists() && jcabiXMLJar.exists()) {
      assertTrue(
          new File("src/test/resources/JarUtilsResources_copy/com/jcabi/ssh/Ssh.class").exists()
      );
      assertTrue(
          new File("src/test/resources/JarUtilsResources_copy/com/jcabi/xml/XML.class").exists()
      );
    }
  }

  private void decompress() {
    try {
      JarUtils.decompressJars(copyDir.getAbsolutePath());
    } catch (RuntimeException e) {
      System.out.println("Error decompressing jars in " + copyDir.getAbsolutePath());
    }
  }

}