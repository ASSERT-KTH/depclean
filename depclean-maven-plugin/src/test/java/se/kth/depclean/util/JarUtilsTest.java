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
  static final File jLibraryEAR = new File("src/test/resources/JarUtilsResources_copy/jlibrary-ear-1.2.ear");
  static final File warWAR = new File("src/test/resources/JarUtilsResources_copy/war-6.2.3.war");

  @BeforeEach
  void setUp() {
    try {
      // make a copy of the directory containing the JAR files
      FileUtils.copyDirectory(
          originalDir,    // If directory is empty/Null then NPE will be thrown.
          copyDir         // If directory is empty/Null then NPE will be thrown.
      );
    } catch (IOException | NullPointerException e) {
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
    if (jcabiSSHJar.exists() && jcabiXMLJar.exists() && warWAR.exists() && jLibraryEAR.exists()) {
      // Decompress all the jar files in the current directory
      decompress();
      assertFalse(FileUtils.directoryContains(copyDir, jcabiSSHJar));
      assertFalse(FileUtils.directoryContains(copyDir, jcabiXMLJar));
      assertFalse(FileUtils.directoryContains(copyDir, jLibraryEAR));
      assertFalse(FileUtils.directoryContains(copyDir, warWAR));
    }
  }

  @Test
  @Order(2)
  @DisplayName("Test that when decompressing JAR files other (decompressed) files are created")
  void whenDecompressJarFiles_thenOtherFilesAreCreated() throws RuntimeException {
    // Assert that POM files are decompressed
    if (jcabiSSHJar.exists() && jcabiXMLJar.exists() && jLibraryEAR.exists() && jcabiXMLJar.exists()) {
      decompress();
      // JAR
      assertTrue(
          new File("src/test/resources/JarUtilsResources_copy/jcabi-ssh-1.6/META-INF/maven/com.jcabi/jcabi-ssh/pom.xml").exists()
      );
      // JAR
      assertTrue(
          new File("src/test/resources/JarUtilsResources_copy/jcabi-xml-0.22.2/META-INF/maven/com.jcabi/jcabi-xml/pom.xml").exists()
      );
      // WAR
      assertTrue(
          new File("src/test/resources/JarUtilsResources_copy/war-6.2.3/META-INF/maven/org.glassfish.main.admingui/war/pom.xml").exists()
      );
      // EAR
      assertTrue(
          new File("src/test/resources/JarUtilsResources_copy/jlibrary-ear-1.2/META-INF/maven/org.jlibrary/jlibrary-ear/pom.xml").exists()
      );
    }
    // Assert that CLASS files in JARs are decompressed
    if (jcabiSSHJar.exists() && jcabiXMLJar.exists() && jLibraryEAR.exists() && jcabiXMLJar.exists()) {
      // JAR
      assertTrue(
          new File("src/test/resources/JarUtilsResources_copy/jcabi-ssh-1.6/com/jcabi/ssh/Ssh.class").exists()
      );
      // JAR
      assertTrue(
          new File("src/test/resources/JarUtilsResources_copy/jcabi-xml-0.22.2/com/jcabi/xml/XML.class").exists()
      );
      // WAR
      assertTrue(
          new File("src/test/resources/JarUtilsResources_copy/war-6.2.3/WEB-INF/lib/console-core-6.2.3/org/glassfish/admingui/util/SunOptionUtil.class").exists()
      );
      // EAR
      assertTrue(
          new File("src/test/resources/JarUtilsResources_copy/jlibrary-ear-1.2/jlibrary/WEB-INF/lib/jcr-1.0/javax/jcr/Item.class").exists()
      );
    }
  }

  private void decompress() {
    try {
      JarUtils.decompress(copyDir.getAbsolutePath());
    } catch (RuntimeException e) {
      System.out.println("Error decompressing jars in " + copyDir.getAbsolutePath());
    }
  }
}