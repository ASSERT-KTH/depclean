package se.kth.depclean.util;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileUtilsTest {

  @BeforeEach
  void setUp() {
  }

  @AfterEach
  void tearDown() {
  }

  @Test
  void deleteDirectory() throws IOException {
    File file = new File("./target/dependency");
    if (file.exists()) {
      FileUtils.deleteDirectory(new File("./target/dependency"));
    }
    assertFalse(file.exists());
  }

}