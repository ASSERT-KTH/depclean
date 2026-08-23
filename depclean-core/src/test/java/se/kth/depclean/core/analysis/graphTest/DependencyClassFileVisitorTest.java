package se.kth.depclean.core.analysis.graphTest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.kth.depclean.core.analysis.asm.DependencyClassFileVisitor;
import se.kth.depclean.core.analysis.asm.ResultCollector;
import se.kth.depclean.core.analysis.graph.DefaultCallGraph;

class DependencyClassFileVisitorTest {

  // Resource class for testing.
  private static final File CLASS_FILE =
      new File("src/test/resources/asmAndGraphResources/ExampleClass.class");
  private static final String CLASS_NAME = "ExampleClass";

  @Test
  @DisplayName(
      "Test that the asm and graph are working together and performing"
          + " their work (Adding classes and dependencies as edges).")
  void test_that_graph_is_collecting_edges_from_asm_correctly() throws IOException {

    ResultCollector resultCollector = new ResultCollector();
    FileInputStream fileInputStream = new FileInputStream(CLASS_FILE);

    DependencyClassFileVisitor visitor = new DependencyClassFileVisitor();
    visitor.visitClass(CLASS_NAME, fileInputStream);

    // Checking for the expected results.
    Assertions.assertTrue(DefaultCallGraph.containsVertex(CLASS_NAME));
    for (String referencedClassMember : resultCollector.getDependencies()) {
      Assertions.assertTrue(DefaultCallGraph.containsEdge(CLASS_NAME, referencedClassMember));
    }

    // Confirming the successful termination of DependencyClassFileVisitor object.
    Assertions.assertTrue(resultCollector.getDependencies().isEmpty());
  }
}
