package se.kth.depclean.core.fake.depmanager;

import static com.google.common.collect.ImmutableSet.of;

import java.nio.file.Path;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import se.kth.depclean.core.fake.FakeDependencyGraph;
import se.kth.depclean.core.model.Dependency;

public class EmptyProjectDependencyManager extends FakeDependencyManager {

  public EmptyProjectDependencyManager(Logger log) {
    super(log, new FakeDependencyGraph() {

      @Override
      public Set<Dependency> directDependencies() {
        return of();
      }

      @Override
      public Set<Dependency> inheritedDirectDependencies() {
        return of();
      }

      @Override
      public Set<Dependency> inheritedTransitiveDependencies() {
        return of();
      }

      @Override
      public Set<Dependency> transitiveDependencies() {
        return of();
      }

      @Override
      public Set<Dependency> allDependencies() {
        return of();
      }

      @Override
      public Set<Dependency> getDependenciesForParent(Dependency parent) {
        return of();
      }
    });
  }

  @Override
  public Set<Path> getOutputDirectories() {
    return of(
        FileUtils.getTempDirectory().toPath()
    );
  }

  @Override
  public Set<Path> getTestOutputDirectories() {
    return of(
        FileUtils.getTempDirectory().toPath()
    );
  }
}
