package se.kth.depclean.core.fake.depmanager;

import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Set;
import org.apache.log4j.Logger;

public class OnlyDirectUsedDependencyManager extends FakeDependencyManager {

  public OnlyDirectUsedDependencyManager(Logger log) {
    super(log);
  }

  @Override
  public Set<Path> getOutputDirectories() {
    return ImmutableSet.of(END_2_END_PATH.resolve("1dep"));
  }

  @Override
  public Set<Path> getTestOutputDirectories() {
    return ImmutableSet.of(END_2_END_PATH.resolve("1dep"));
  }
}
