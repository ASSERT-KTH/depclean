package se.kth.depclean.util;

import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.model.Factory;
import guru.nidi.graphviz.model.Graph;
import guru.nidi.graphviz.model.Link;
import guru.nidi.graphviz.model.Node;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import se.kth.depclean.core.model.Dependency;
import se.kth.depclean.graph.MavenDependencyGraph;

/**
 * Utility class for exporting Maven dependency graphs to Graphviz images.
 */
public class GraphvizExporter {

  /**
   * Exports the given MavenDependencyGraph as a Graphviz image.
   *
   * @param graph      The MavenDependencyGraph to export.
   * @param outputFile The file to write the image to.
   * @throws IOException if writing fails.
   */
  public static void export(MavenDependencyGraph graph, File outputFile) throws IOException {
    Map<Dependency, Node> nodeMap = new HashMap<>();
    // Create nodes for all dependencies
    for (Dependency dep : graph.allDependencies()) {
      String label = dep.getGroupId() + ":" + dep.getDependencyId() + ":" + dep.getVersion();
      nodeMap.put(dep, Factory.node(label));
    }
    // Create edges
    Graph g = Factory.graph("dependencyGraph").directed();
    for (Dependency parent : graph.allDependencies()) {
      Set<Dependency> children = graph.getDependenciesForParent(parent);
      Node parentNode = nodeMap.get(parent);
      for (Dependency child : children) {
        Node childNode = nodeMap.get(child);
        if (parentNode != null && childNode != null) {
          g = g.with(parentNode.link(Link.to(childNode)));
        }
      }
    }
    // Render to file
    Graphviz.fromGraph(g).render(Format.PNG).toFile(outputFile);
  }
}