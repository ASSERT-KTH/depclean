package se.kth.depclean.core.analysis;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import org.jgrapht.graph.AbstractBaseGraph;
import org.jgrapht.graph.DefaultEdge;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.kth.depclean.core.analysis.asm.DependencyClassFileVisitor;
import se.kth.depclean.core.analysis.asm.ResultCollector;
import se.kth.depclean.core.analysis.graph.DefaultCallGraph;

public class DependencyClassFileVisitorTest {

  // Resource class for testing.
  private static final File classFile = new File("src/test/resources/ClassFileVisitorResources/ExampleClass.class");
  private static final String className = "ExampleClass";

  @Test
  @DisplayName("Test that the asm and graph are working together and performing"
          + " their work (Adding classes and dependencies as edges).")
  void test_that_graph_is_collecting_edges_from_asm_correctly() throws IOException {

    ResultCollector resultCollector = new ResultCollector();
    FileInputStream fileInputStream = new FileInputStream(classFile);
    AbstractBaseGraph<String, DefaultEdge> directedGraph = DefaultCallGraph.getDirectedGraph();

    DependencyClassFileVisitor visitor = new DependencyClassFileVisitor();
    visitor.visitClass(className, fileInputStream);

    // Checking for the expected results.
    Assertions.assertTrue(directedGraph.containsVertex(className));
    for (String referencedClassMember : resultCollector.getDependencies()) {
      Assertions.assertTrue(directedGraph.containsEdge(className, referencedClassMember));
    }

    // Confirming the successful termination of DependencyClassFileVisitor object.
    Assertions.assertTrue(resultCollector.getDependencies().isEmpty());
  }
}
