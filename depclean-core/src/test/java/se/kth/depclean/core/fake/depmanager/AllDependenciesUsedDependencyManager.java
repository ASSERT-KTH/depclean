package se.kth.depclean.core.fake.depmanager;

import static com.google.common.collect.ImmutableSet.of;

import java.nio.file.Path;
import java.util.Set;
import org.apache.log4j.Logger;

public class AllDependenciesUsedDependencyManager extends FakeDependencyManager {

  public AllDependenciesUsedDependencyManager(Logger log) {
    super(log);
  }

  @Override
  public Set<Path> getOutputDirectories() {
    return of(END_2_END_PATH.resolve("3deps"));
  }

  @Override
  public Set<Path> getTestOutputDirectories() {
    return of(END_2_END_PATH.resolve("3deps"));
  }
}
