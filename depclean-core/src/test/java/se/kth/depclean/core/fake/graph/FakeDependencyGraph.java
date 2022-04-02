package se.kth.depclean.core.fake.graph;

import com.google.common.collect.ImmutableSet;
import static com.google.common.collect.ImmutableSet.of;
import se.kth.depclean.core.analysis.graph.DependencyGraph;
import se.kth.depclean.core.model.Dependency;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

public interface FakeDependencyGraph extends DependencyGraph {

  Path RESOURCES_PATH = Paths.get("src/test/resources");
  Path END_2_END_PATH = RESOURCES_PATH.resolve("end2end");

  Dependency COMMONS_IO_DEPENDENCY = createDependency("commons-io");
  Dependency COMMONS_LANG_DEPENDENCY = createDependency("commons-lang3");
  Dependency COMMONS_LOGGING_DEPENDENCY = createDependency("commons-logging-api");

  Set<Path> getOutputDirectories();

  Set<Path> getTestOutputDirectories();

  @Override
  default Dependency projectCoordinates() {
    return new Dependency("se.kth.depclean", "test", "1.0", null);
  }

  @Override
  default Set<Dependency> allDependencies() {
    return ImmutableSet.<Dependency>builder()
        .addAll(directDependencies())
        .addAll(inheritedDependencies())
        .addAll(transitiveDependencies())
        .build();
  }

  @Override
  default Set<Dependency> directDependencies() {
    return of(COMMONS_IO_DEPENDENCY);
  }

  @Override
  default Set<Dependency> inheritedDependencies() {
    return of(COMMONS_LANG_DEPENDENCY);
  }

  @Override
  default Set<Dependency> transitiveDependencies() {
    return of(COMMONS_LOGGING_DEPENDENCY);
  }

  @Override
  default Set<Dependency> getDependenciesForParent(Dependency parent) {
    if (COMMONS_IO_DEPENDENCY.equals(parent) || COMMONS_LANG_DEPENDENCY.equals(parent)) {
      return of(COMMONS_LOGGING_DEPENDENCY);
    }
    return of();
  }

  static Dependency createDependency(String name) {
    final File jarFile = new File("src/test/resources/analysisResources/" + name + ".jar");
    return new Dependency(
        "se.kth.depclean.core.test",
        name,
        "1.0.0",
        "compile",
        jarFile
    );
  }
}
