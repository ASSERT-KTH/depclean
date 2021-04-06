package se.kth.depclean.core.analysis;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Slf4j
class CollectorClassFileVisitorTest {

  private CollectorClassFileVisitorTest() throws FileNotFoundException {
    // constructor to throw exception;
  }
  private static final String path = "src/test/resources/ClassFileVisitorResources/test.class";
  private static final File file = new File(path);
  private static final String name = "test";
  private static final CollectorClassFileVisitor visit = new CollectorClassFileVisitor();
  final FileInputStream in = new FileInputStream(file);

  @BeforeEach
  void setUp() {
    try {
      visit.visitClass(name , in);
    } catch (IllegalArgumentException e) {
      log.error("Failed to visit the class at path : " + path);
    }
  }

  @Test
  @DisplayName("Test that the class is visited and added to the set")
  void dummy() {
    Set<String> classes = new HashSet<>(visit.getClasses());
    if (classes.isEmpty()) {
      visit.visitClass(name, in);
    }
    Assertions.assertFalse(classes.isEmpty());
  }
}
