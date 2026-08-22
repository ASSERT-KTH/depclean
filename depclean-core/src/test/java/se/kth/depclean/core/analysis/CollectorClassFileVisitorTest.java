package se.kth.depclean.core.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CollectorClassFileVisitorTest {

  private static final Logger log = LoggerFactory.getLogger(CollectorClassFileVisitorTest.class);

  private static final File classFile = new File("src/test/resources/analysisResources/test.class");
  private static final String className = "test";
  private static final CollectorClassFileVisitor collector = new CollectorClassFileVisitor();

  @Test
  @DisplayName("Test that the class is visited and added to the set of visited classes")
  void whenClassIsVisited_thenItIsAddedToTheSetOfVisitedClasses() throws FileNotFoundException {
    FileInputStream fileInputStream = new FileInputStream(classFile);
    try {
      collector.visitClass(className, fileInputStream);
    } catch (IllegalArgumentException e) {
      log.error("Failed to visit the class at: " + classFile.getAbsolutePath());
    }
    assertThat(collector.getClasses()).isNotEmpty();
  }
}
