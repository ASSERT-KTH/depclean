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
  private static final File classFile =
      new File("src/test/resources/asmAndGraphResources/ExampleClass.class");
  private static final String className = "ExampleClass";

  @Test
  @DisplayName(
      "Test that the asm and graph are working together and performing"
          + " their work (Adding classes and dependencies as edges).")
  void test_that_graph_is_collecting_edges_from_asm_correctly() throws IOException {

    ResultCollector resultCollector = new ResultCollector();
    FileInputStream fileInputStream = new FileInputStream(classFile);

    DependencyClassFileVisitor visitor = new DependencyClassFileVisitor();
    visitor.visitClass(className, fileInputStream);

    // Checking for the expected results.
    Assertions.assertTrue(DefaultCallGraph.containsVertex(className));
    for (String referencedClassMember : resultCollector.getDependencies()) {
      Assertions.assertTrue(DefaultCallGraph.containsEdge(className, referencedClassMember));
    }

    // Confirming the successful termination of DependencyClassFileVisitor object.
    Assertions.assertTrue(resultCollector.getDependencies().isEmpty());
  }
}
