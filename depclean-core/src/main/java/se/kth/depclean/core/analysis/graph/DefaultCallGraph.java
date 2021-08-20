/*
 * Copyright (c) 2020, CASTOR Software Research Centre (www.castor.kth.se)
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package se.kth.depclean.core.analysis.graph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.jgrapht.graph.AbstractBaseGraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.DepthFirstIterator;

/**
 * A directed graph G = (V, E) where V is a set of classes and E is a set of edges
 * representing class member calls between the classes in V.
 */
public class DefaultCallGraph {

  private static final AbstractBaseGraph<String, DefaultEdge> directedGraph =
      new DefaultDirectedGraph<>(DefaultEdge.class);
  private static final Set<String> projectVertices = new HashSet<>();
  private static final Map<String, Set<String>> usagesPerClass = new HashMap<>();

  /**
   * Add an edge to the call graph of classes.
   *
   * @param clazz                  The source.
   * @param referencedClassMembers The target.
   */
  public static void addEdge(String clazz, Set<String> referencedClassMembers) {
    directedGraph.addVertex(clazz);
    for (String referencedClassMember : referencedClassMembers) {
      if (!directedGraph.containsVertex(referencedClassMember)) {
        directedGraph.addVertex(referencedClassMember);
      }
      directedGraph.addEdge(clazz, referencedClassMember);
      projectVertices.add(clazz);

      // Save the pair [class -> referencedClassMember] for further analysis
      addReferencedClassMember(clazz, referencedClassMember);
    }
  }

  /**
   * Traverses the call graph to obtain a set of all the reachable classes from a set of classes (ie., vertices in the
   * graph).
   *
   * @param projectClasses The classes in the Maven project.
   * @return All the referenced classes.
   */
  public static Set<String> referencedClassMembers(Set<String> projectClasses) {
    // System.out.println("project classes: " + projectClasses);
    Set<String> allReferencedClassMembers = new HashSet<>();
    for (String projectClass : projectClasses) {
      allReferencedClassMembers.addAll(traverse(projectClass));
    }
    // System.out.println("All referenced class members: " + allReferencedClassMembers);
    return allReferencedClassMembers;
  }

  /**
   * Traverse the graph using DFS.
   *
   * @param start The starting vertex.
   * @return The set of all visited vertices.
   */
  private static Set<String> traverse(String start) {
    Set<String> referencedClassMembers = new HashSet<>();
    Iterator<String> iterator = new DepthFirstIterator<>(directedGraph, start);
    while (iterator.hasNext()) {
      referencedClassMembers.add(iterator.next());
    }
    return referencedClassMembers;
  }

  private static void addReferencedClassMember(String clazz, String referencedClassMember) {
    // System.out.println("\t" + clazz + " -> " + referencedClassMember);
    Set<String> s = usagesPerClass.computeIfAbsent(clazz, k -> new HashSet<>());
    s.add(referencedClassMember);
  }

  public static AbstractBaseGraph<String, DefaultEdge> getDirectedGraph() {
    return directedGraph;
  }

  public static Set<String> getProjectVertices() {
    return projectVertices;
  }

  public static Set<String> getVertices() {
    return directedGraph.vertexSet();
  }

  public static void cleanDirectedGraph() {
    directedGraph.vertexSet().clear();
    directedGraph.edgeSet().clear();
  }

  public Map<String, Set<String>> getUsagesPerClass() {
    return usagesPerClass;
  }

}
