package se.kth.depclean.core.analysis;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultClassAnalyzerTest {

  // This jar file only contains "ExampleClass.class"
  File jarFile = new File("src/test/resources/analysisResources/ExampleClass.jar");
  File directoryFile = new File("src/test/resources/asmAndGraphResources");

  @Test
  @DisplayName("Test that the DCA is accepting a jar and returning its classes successfully.")
  void DCA_jar_test() throws IOException {
    URL url = jarFile.toURI().toURL();
    Assertions.assertFalse(analyze(url));
  }

  @Test
  @DisplayName("Test that the DCA is accepting a directory and returning its classes successfully.")
  void DCA_directory_test() throws IOException {
    URL url = directoryFile.toURI().toURL();
    Assertions.assertFalse(analyze(url));
  }

  // Utility method to get the classes.
  boolean analyze(URL url) throws IOException {
    ClassAnalyzer classAnalyzer = new DefaultClassAnalyzer();
    return classAnalyzer.analyze(url).isEmpty();
  }
}
