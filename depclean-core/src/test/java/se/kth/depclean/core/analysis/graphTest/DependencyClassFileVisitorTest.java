package se.kth.depclean.core.analysis.graphTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.kth.depclean.core.analysis.asm.DependencyClassFileVisitor;
import se.kth.depclean.core.analysis.graph.DefaultCallGraph;

class DependencyClassFileVisitorTest {

  // Resource class for testing.
  private static final File CLASS_FILE =
      new File("src/test/resources/asmAndGraphResources/ExampleClass.class");
  private static final String CLASS_NAME = "ExampleClass";

  // Types referenced by ExampleClass.class (see javap -c): Object ctor, StringBuilder,
  // System.out (PrintStream) and String parameters/return values.
  private static final Set<String> EXPECTED_REFERENCES =
      Set.of(
          "java.lang.Object",
          "java.lang.String",
          "java.lang.StringBuilder",
          "java.lang.System",
          "java.io.PrintStream");

  @BeforeEach
  @AfterEach
  void resetSharedGraph() {
    // DefaultCallGraph is static; isolate this test from any other test in the JVM.
    DefaultCallGraph.clear();
  }

  @Test
  @DisplayName(
      "Test that the asm and graph are working together and performing"
          + " their work (Adding classes and dependencies as edges).")
  void test_that_graph_is_collecting_edges_from_asm_correctly() throws IOException {
    DependencyClassFileVisitor visitor = new DependencyClassFileVisitor();
    try (InputStream in = new FileInputStream(CLASS_FILE)) {
      visitor.visitClass(CLASS_NAME, in);
    }

    // The visited class becomes a vertex and a project vertex.
    assertThat(DefaultCallGraph.containsVertex(CLASS_NAME)).isTrue();
    assertThat(DefaultCallGraph.getProjectVertices()).containsExactly(CLASS_NAME);

    // The references found by ASM were recorded as outgoing edges of the class.
    Set<String> referencedClassMembers = DefaultCallGraph.getUsagesPerClass().get(CLASS_NAME);
    assertThat(referencedClassMembers).isNotNull().isNotEmpty().containsAll(EXPECTED_REFERENCES);
    for (String referencedClassMember : referencedClassMembers) {
      assertThat(DefaultCallGraph.containsEdge(CLASS_NAME, referencedClassMember))
          .as("edge %s -> %s", CLASS_NAME, referencedClassMember)
          .isTrue();
      assertThat(DefaultCallGraph.containsVertex(referencedClassMember))
          .as("vertex %s", referencedClassMember)
          .isTrue();
    }

    // The per-class collector is reset once its edges are in the graph, so that the
    // references of the next visited class are not attributed to this one.
    assertThat(visitor.getDependencies()).isEmpty();
  }

  @Test
  @DisplayName("A reused visitor attributes each class's references only to that class.")
  void test_that_reused_visitor_does_not_leak_references_between_classes() throws IOException {
    DependencyClassFileVisitor visitor = new DependencyClassFileVisitor();
    try (InputStream in = new FileInputStream(CLASS_FILE)) {
      visitor.visitClass("First", in);
    }
    try (InputStream in = new FileInputStream(CLASS_FILE)) {
      visitor.visitClass("Second", in);
    }

    Set<String> firstEdges = DefaultCallGraph.getUsagesPerClass().get("First");
    Set<String> secondEdges = DefaultCallGraph.getUsagesPerClass().get("Second");
    assertThat(firstEdges).containsAll(EXPECTED_REFERENCES);
    assertThat(secondEdges).containsExactlyInAnyOrderElementsOf(firstEdges);
    assertThat(DefaultCallGraph.containsEdge("First", "Second")).isFalse();
    assertThat(DefaultCallGraph.containsEdge("Second", "First")).isFalse();
    assertThat(DefaultCallGraph.getProjectVertices()).containsExactlyInAnyOrder("First", "Second");
    assertThat(visitor.getDependencies()).isEmpty();
  }

  @Test
  @DisplayName("Invalid bytecode is skipped without throwing and without touching the graph.")
  void test_that_invalid_class_file_is_skipped() {
    DependencyClassFileVisitor visitor = new DependencyClassFileVisitor();
    InputStream garbage = new ByteArrayInputStream(new byte[] {0xC, 0xA, 0xF, 0xE});

    assertThatCode(() -> visitor.visitClass("Broken", garbage)).doesNotThrowAnyException();

    assertThat(DefaultCallGraph.containsVertex("Broken")).isFalse();
    assertThat(DefaultCallGraph.getProjectVertices()).isEmpty();
    assertThat(DefaultCallGraph.getUsagesPerClass()).isEmpty();
    assertThat(visitor.getDependencies()).isEmpty();
  }
}
