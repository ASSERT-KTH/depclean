package se.kth.depclean.core.analysis.graph;

import java.util.Set;
import se.kth.depclean.core.model.Dependency;

/**
 * Should build a graph of dependencies, so that it can be requested for any representation of this graph.
 */
public interface DependencyGraph {

  Dependency projectCoordinates();

  Set<Dependency> directDependencies();

  Set<Dependency> transitiveDependencies();

  Set<Dependency> inheritedDirectDependencies();

  Set<Dependency> inheritedTransitiveDependencies();

  Set<Dependency> allDependencies();

  Set<Dependency> getDependenciesForParent(Dependency parent);
}
