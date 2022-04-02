package se.kth.depclean.core.fake.graph;

import static com.google.common.collect.ImmutableSet.of;
import java.nio.file.Path;
import java.util.Set;

public class OnlyDirectAndInheritedUsedDependencyGraph implements FakeDependencyGraph {

  @Override
  public Set<Path> getOutputDirectories() {
    return of(END_2_END_PATH.resolve("2deps"));
  }

  @Override
  public Set<Path> getTestOutputDirectories() {
    return of(END_2_END_PATH.resolve("2deps"));
  }
}
