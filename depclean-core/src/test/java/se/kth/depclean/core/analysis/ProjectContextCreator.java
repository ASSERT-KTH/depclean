package se.kth.depclean.core.analysis;

import static org.assertj.core.util.Sets.newHashSet;

import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;
import lombok.AllArgsConstructor;
import se.kth.depclean.core.analysis.graph.DependencyGraph;
import se.kth.depclean.core.model.ClassName;
import se.kth.depclean.core.model.Dependency;
import se.kth.depclean.core.model.ProjectContext;
import se.kth.depclean.core.model.Scope;

public interface ProjectContextCreator {

  ClassName COMMONS_IO_CLASS = new ClassName("org.apache.commons.io.IOUtils");
  ClassName COMMONS_LANG_CLASS = new ClassName("org.apache.commons.lang3.ArrayUtils");
  ClassName COMMONS_LOGGING_CLASS = new ClassName("org.apache.commons.logging.Log");
  ClassName JUNIT_CLASS = new ClassName("org.junit.jupiter.engine.JupiterTestEngine");
  ClassName UNKNOWN_CLASS = new ClassName("com.unknown.Unknown");
  Dependency COMMONS_IO_DEPENDENCY = createDependency("commons-io");
  Dependency COMMONS_MATH_DEPENDENCY = createDependency("commons-math");
  Dependency COMMONS_LANG_DEPENDENCY = createDependency("commons-lang3");
  Dependency COMMONS_LOGGING_DEPENDENCY = createDependency("commons-logging-api");
  Dependency JUNIT_DEPENDENCY = createTestDependency("junit-jupiter");
  Dependency UNKNOWN_DEPENDENCY = createDependency("unknown");

  default ProjectContext createContext() {
    return new ProjectContext(
        new TestDependencyGraph(
            createDependency("ExampleClass"),
            ImmutableSet.of(COMMONS_IO_DEPENDENCY, JUNIT_DEPENDENCY),
            ImmutableSet.of(COMMONS_LANG_DEPENDENCY),
            ImmutableSet.of(COMMONS_MATH_DEPENDENCY),
            ImmutableSet.of(COMMONS_LOGGING_DEPENDENCY)),
        ImmutableSet.of(Paths.get("target/classes")),
        ImmutableSet.of(Paths.get("target/test-classes")),
        Paths.get("/main"),
        Paths.get("/test"),
        Paths.get(""),
        Collections.emptySet(),
        Collections.emptySet(),
        Collections.emptySet());
  }

  default ProjectContext createContextIgnoringTests() {
    return new ProjectContext(
        new TestDependencyGraph(
            createDependency("ExampleClass"),
            ImmutableSet.of(COMMONS_IO_DEPENDENCY, JUNIT_DEPENDENCY),
            ImmutableSet.of(COMMONS_LANG_DEPENDENCY),
            ImmutableSet.of(COMMONS_MATH_DEPENDENCY),
            ImmutableSet.of(COMMONS_LOGGING_DEPENDENCY)),
        ImmutableSet.of(Paths.get("target/classes")),
        ImmutableSet.of(Paths.get("target/test-classes")),
        Paths.get("/main"),
        Paths.get("/test"),
        Paths.get(""),
        ImmutableSet.of(new Scope("test")),
        Collections.emptySet(),
        Collections.emptySet());
  }

  default ProjectContext createContextIgnoringDependency() {
    return new ProjectContext(
        new TestDependencyGraph(
            createDependency("ExampleClass"),
            ImmutableSet.of(COMMONS_IO_DEPENDENCY),
            ImmutableSet.of(COMMONS_LANG_DEPENDENCY),
            ImmutableSet.of(COMMONS_MATH_DEPENDENCY),
            ImmutableSet.of(COMMONS_LOGGING_DEPENDENCY)),
        ImmutableSet.of(Paths.get("target/classes")),
        ImmutableSet.of(Paths.get("target/test-classes")),
        Paths.get("/main"),
        Paths.get("/test"),
        Paths.get(""),
        ImmutableSet.of(new Scope("test")),
        ImmutableSet.of(COMMONS_IO_DEPENDENCY),
        Collections.emptySet());
  }

  static Dependency createDependency(String name) {
    final File jarFile = new File("src/test/resources/analysisResources/" + name + ".jar");
    return new Dependency("se.kth.depclean.core.analysis", name, "1.0.0", "compile", jarFile);
  }

  static Dependency createTestDependency(String name) {
    final File jarFile = new File("src/test/resources/analysisResources/" + name + ".jar");
    return new Dependency("se.kth.depclean.core.analysis", name, "1.0.0", "test", jarFile);
  }

  @AllArgsConstructor
  class TestDependencyGraph implements DependencyGraph {

    private final Dependency projectCoordinates;
    private final Set<Dependency> directDependencies;
    private final Set<Dependency> inheritedDirectDependencies;
    private final Set<Dependency> inheritedTransitiveDependencies;
    private final Set<Dependency> transitiveDependencies;

    @Override
    public Dependency projectCoordinates() {
      return projectCoordinates;
    }

    @Override
    public Set<Dependency> directDependencies() {
      return directDependencies;
    }

    @Override
    public Set<Dependency> inheritedDirectDependencies() {
      return inheritedDirectDependencies;
    }

    @Override
    public Set<Dependency> inheritedTransitiveDependencies() {
      return inheritedTransitiveDependencies;
    }

    @Override
    public Set<Dependency> transitiveDependencies() {
      return transitiveDependencies;
    }

    @Override
    public Set<Dependency> allDependencies() {
      final Set<Dependency> dependencies = newHashSet(directDependencies);
      dependencies.addAll(inheritedDirectDependencies);
      dependencies.addAll(inheritedTransitiveDependencies);
      dependencies.addAll(transitiveDependencies);
      return ImmutableSet.copyOf(dependencies);
    }

    @Override
    public Set<Dependency> getDependenciesForParent(Dependency parent) {
      if (parent.equals(COMMONS_LANG_DEPENDENCY) || parent.equals(COMMONS_IO_DEPENDENCY)) {
        return ImmutableSet.of(COMMONS_LOGGING_DEPENDENCY);
      }
      return ImmutableSet.of();
    }
  }
}
