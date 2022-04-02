package se.kth.depclean.core.fake.graph;

import static com.google.common.collect.ImmutableSet.of;

import java.nio.file.Path;
import java.util.Set;

public class NoDependencyUsedDependencyGraph implements FakeDependencyGraph {

  @Override
  public Set<Path> getOutputDirectories() {
    return of(END_2_END_PATH.resolve("nodep"));
  }

  @Override
  public Set<Path> getTestOutputDirectories() {
    return of(END_2_END_PATH.resolve("nodep"));
  }
}
