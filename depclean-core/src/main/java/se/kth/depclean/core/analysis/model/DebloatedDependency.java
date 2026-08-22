package se.kth.depclean.core.analysis.model;

import java.util.Set;
import se.kth.depclean.core.model.Dependency;

/** A debloated dependency. */
public class DebloatedDependency extends Dependency {

  private final Set<Dependency> exclusions;

  public DebloatedDependency(Dependency dependency, Set<Dependency> exclusions) {
    super(dependency);
    this.exclusions = exclusions;
  }

  public Set<Dependency> getExclusions() {
    return exclusions;
  }
}
