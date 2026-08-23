package se.kth.depclean.core.analysis.model;

import java.util.Set;
import org.jspecify.annotations.Nullable;
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

  // Exclusions are intentionally not part of the dependency's identity.
  @Override
  public boolean equals(@Nullable Object o) {
    return super.equals(o);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }
}
