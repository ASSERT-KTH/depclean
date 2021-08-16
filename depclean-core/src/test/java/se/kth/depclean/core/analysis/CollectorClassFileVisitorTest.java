package se.kth.depclean.core.analysis;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Slf4j
class CollectorClassFileVisitorTest {

  private static final File classFile = new File(
          "src/test/resources/analysisResources/test.class");
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
    Set<String> visitedClasses = new HashSet<>(collector.getClasses());
    Assertions.assertFalse(visitedClasses.isEmpty());
  }
}
