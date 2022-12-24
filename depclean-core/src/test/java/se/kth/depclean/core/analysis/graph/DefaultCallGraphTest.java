package se.kth.depclean.core.analysis.graph;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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
    DefaultCallGraph.addEdge("A", new HashSet<>(Arrays.asList("B", "C", "D")));
    DefaultCallGraph.addEdge("D", new HashSet<>(Arrays.asList("E", "F")));
    DefaultCallGraph.addEdge("F", new HashSet<>(Arrays.asList("G", "H")));
    DefaultCallGraph.addEdge("I", new HashSet<>(Arrays.asList("J")));
  }

  @Test
  void referencedClassMembers() {
    HashSet<String> projectClasses = new HashSet<>(Arrays.asList("A"));
    Set<String> referenced = DefaultCallGraph.referencedClassMembers(projectClasses);
    // note that there is no path from A to J
    Assertions.assertEquals(new HashSet<>(Arrays.asList("A", "B", "C", "D", "E", "F", "G", "H")), referenced);
  }

  @Test
  void getProjectVertices() {
    Set<String> projectVertices = DefaultCallGraph.getProjectVertices();
    Assertions.assertEquals(new HashSet<>(Arrays.asList("A", "D", "F", "I")), projectVertices);
  }

  @Test
  void getUsagesPerClass() {
    Map<String, Set<String>> usagesPerClass = DefaultCallGraph.getUsagesPerClass();
    Map<String, Set<String>> usagesExpected = new HashMap<>();
    usagesExpected.put("A", new HashSet<>(Arrays.asList("B", "C", "D")));
    usagesExpected.put("D", new HashSet<>(Arrays.asList("E", "F")));
    usagesExpected.put("F", new HashSet<>(Arrays.asList("G", "H")));
    usagesExpected.put("I", new HashSet<>(Arrays.asList("J")));
    Assertions.assertEquals(usagesExpected, usagesPerClass);
  }

  @AfterEach
  void tearDown() {
    DefaultCallGraph.clear();
  }
}