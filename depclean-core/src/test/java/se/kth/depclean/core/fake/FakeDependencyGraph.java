package se.kth.depclean.core.fake;

import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.util.Set;
import se.kth.depclean.core.analysis.graph.DependencyGraph;
import se.kth.depclean.core.model.Dependency;

public class FakeDependencyGraph implements DependencyGraph {

  Dependency COMMONS_IO_DEPENDENCY = createDependency("commons-io");
  Dependency COMMONS_LANG_DEPENDENCY = createDependency("commons-lang3");
  Dependency COMMONS_LOGGING_DEPENDENCY = createDependency("commons-logging-api");

  @Override
  public Dependency projectCoordinates() {
    return new Dependency("se.kth.depclean", "test", "1.0", null);
  }

  @Override
  public Set<Dependency> allDependencies() {
    return ImmutableSet.<Dependency>builder()
        .addAll(directDependencies())
        .addAll(transitiveDependencies())
        .addAll(inheritedDirectDependencies())
        .addAll(inheritedTransitiveDependencies())
        .build();
  }

  @Override
  public Set<Dependency> directDependencies() {
    return ImmutableSet.of(COMMONS_IO_DEPENDENCY);
  }

  @Override
  public Set<Dependency> transitiveDependencies() {
    return ImmutableSet.of(COMMONS_LOGGING_DEPENDENCY);
  }

  @Override
  public Set<Dependency> inheritedDirectDependencies() {
    return ImmutableSet.of(COMMONS_LANG_DEPENDENCY);
  }

  @Override
  public Set<Dependency> inheritedTransitiveDependencies() {
    return ImmutableSet.of(COMMONS_LANG_DEPENDENCY);
  }

  @Override
  public Set<Dependency> getDependenciesForParent(Dependency parent) {
    if (COMMONS_IO_DEPENDENCY.equals(parent) || COMMONS_LANG_DEPENDENCY.equals(parent)) {
      return ImmutableSet.of(COMMONS_LOGGING_DEPENDENCY);
    }
    return ImmutableSet.of();
  }

  static Dependency createDependency(String name) {
    final File jarFile = new File("src/test/resources/analysisResources/" + name + ".jar");
    return new Dependency("se.kth.depclean.core.test", name, "1.0.0", "compile", jarFile);
  }
}
