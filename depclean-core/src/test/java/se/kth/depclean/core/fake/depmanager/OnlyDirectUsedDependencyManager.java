package se.kth.depclean.core.fake.depmanager;

import static com.google.common.collect.ImmutableSet.of;
import org.apache.log4j.Logger;
import java.nio.file.Path;
import java.util.Set;

public class OnlyDirectUsedDependencyManager extends FakeDependencyManager {

  public OnlyDirectUsedDependencyManager(Logger log) {
    super(log);
  }

  @Override
  public Set<Path> getOutputDirectories() {
    return of(END_2_END_PATH.resolve("1dep"));
  }

  @Override
  public Set<Path> getTestOutputDirectories() {
    return of(END_2_END_PATH.resolve("1dep"));
  }
}
