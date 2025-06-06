package se.kth.depclean.core.analysis.graph;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@Slf4j
class DefaultCallGraphTest {

  @BeforeEach
  void setUp() {
    DefaultCallGraph.addEdge("A", Set.of("B", "C", "D"));
    DefaultCallGraph.addEdge("D", Set.of("E", "F"));
    DefaultCallGraph.addEdge("F", Set.of("G", "H"));
    DefaultCallGraph.addEdge("I", Set.of("J"));
  }

  @Test
  void referencedClassMembers() {
    Set<String> referenced = DefaultCallGraph.referencedClassMembers(Set.of("A"));
    // note that there is no path from A to J
    Assertions.assertEquals(Set.of("A", "B", "C", "D", "E", "F", "G", "H"), referenced);
  }

  @Test
  void getProjectVertices() {
    Set<String> projectVertices = DefaultCallGraph.getProjectVertices();
    Assertions.assertEquals(Set.of("A", "D", "F", "I"), projectVertices);
  }

  @Test
  void getUsagesPerClass() {
    Map<String, Set<String>> usagesPerClass = DefaultCallGraph.getUsagesPerClass();
    Map<String, Set<String>> usagesExpected = new HashMap<>();
    usagesExpected.put("A", Set.of("B", "C", "D"));
    usagesExpected.put("D", Set.of("E", "F"));
    usagesExpected.put("F", Set.of("G", "H"));
    usagesExpected.put("I", Set.of("J"));
    Assertions.assertEquals(usagesExpected, usagesPerClass);
  }

  @AfterEach
  void tearDown() {
    DefaultCallGraph.clear();
  }
}