package se.kth.depclean.core.analysis;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Set;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;

public class DefaultClassAnalyzerTest {

  File file = new File("src/test/resources/analysisResources/ExampleClass.jar");

  @Test
  @DisplayName("Test for DefaultClassAnalyzer")
  public void DCA_test() throws IOException {
    ClassAnalyzer classAnalyzer = new DefaultClassAnalyzer();
    URL url = file.toURI().toURL();
    Set<String> classes = classAnalyzer.analyze(url);
    Assertions.assertFalse(classes.isEmpty());
  }
}
