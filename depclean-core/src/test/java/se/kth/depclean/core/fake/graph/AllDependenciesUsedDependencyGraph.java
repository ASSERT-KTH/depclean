package se.kth.depclean.core.fake.graph;

import static com.google.common.collect.ImmutableSet.of;

import java.nio.file.Path;
import java.util.Set;

public class AllDependenciesUsedDependencyGraph implements FakeDependencyGraph {

  @Override
  public Set<Path> getOutputDirectories() {
    return of(END_2_END_PATH.resolve("3deps"));
  }

  @Override
  public Set<Path> getTestOutputDirectories() {
    return of(END_2_END_PATH.resolve("3deps"));
  }
}
