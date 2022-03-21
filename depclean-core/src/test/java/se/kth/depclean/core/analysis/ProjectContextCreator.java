package se.kth.depclean.core.analysis;

import static com.google.common.collect.ImmutableSet.of;
import se.kth.depclean.core.analysis.model.ClassName;
import se.kth.depclean.core.analysis.model.DependencyCoordinate;
import se.kth.depclean.core.analysis.model.ProjectContext;
import se.kth.depclean.core.analysis.model.Scope;
import java.io.File;
import java.nio.file.Paths;
import java.util.Collections;

public interface ProjectContextCreator {

  ClassName COMMONS_IO_CLASS = new ClassName("org.apache.commons.io.IOUtils");
  ClassName COMMONS_LANG_CLASS = new ClassName("org.apache.commons.lang.ArrayUtils");
  ClassName COMMONS_LOGGING_CLASS = new ClassName("org.apache.commons.logging.Log");
  ClassName JUNIT_CLASS = new ClassName("org.junit.jupiter.engine.JupiterTestEngine");
  ClassName UNKNOWN_CLASS = new ClassName("com.unknown.Unknown");
  DependencyCoordinate COMMONS_IO_DEPENDENCY = createDependency("commons-io");
  DependencyCoordinate COMMONS_LANG_DEPENDENCY = createDependency("commons-lang");
  DependencyCoordinate COMMONS_LOGGING_DEPENDENCY = createDependency("commons-logging-api");
  DependencyCoordinate JUNIT_DEPENDENCY = createTestDependency("junit-jupiter");
  DependencyCoordinate UNKNOWN_DEPENDENCY = createDependency("unknown");

  default ProjectContext createContext() {
    return new ProjectContext(
        createDependency("ExampleClass"),
        of(COMMONS_IO_DEPENDENCY, JUNIT_DEPENDENCY),
        of(COMMONS_LANG_DEPENDENCY),
        of(COMMONS_LOGGING_DEPENDENCY),
        Paths.get("main/resources"),
        Paths.get("test/resources"),
        Collections.emptySet(),
        Collections.emptySet(),
        Collections.emptySet()
    );
  }

  default ProjectContext createContextIgnoringTests() {
    return new ProjectContext(
        createDependency("ExampleClass"),
        of(COMMONS_IO_DEPENDENCY, JUNIT_DEPENDENCY),
        of(COMMONS_LANG_DEPENDENCY),
        of(COMMONS_LOGGING_DEPENDENCY),
        Paths.get("main/resources"),
        Paths.get("test/resources"),
        of(new Scope("test")),
        Collections.emptySet(),
        Collections.emptySet()
    );
  }

  default ProjectContext createContextIgnoringDependency() {
    return new ProjectContext(
        createDependency("ExampleClass"),
        of(COMMONS_IO_DEPENDENCY),
        of(COMMONS_LANG_DEPENDENCY),
        of(COMMONS_LOGGING_DEPENDENCY),
        Paths.get("main/resources"),
        Paths.get("test/resources"),
        of(new Scope("test")),
        of(COMMONS_IO_DEPENDENCY),
        Collections.emptySet()
    );
  }

  static DependencyCoordinate createDependency(String name) {
    final File jarFile = new File("src/test/resources/analysisResources/" + name + ".jar");
    return new DependencyCoordinate(
        "se.kth.depclean.core.analysis",
        name,
        "1.0.0",
        "compile",
        jarFile
    );
  }

  static DependencyCoordinate createTestDependency(String name) {
    final File jarFile = new File("src/test/resources/analysisResources/" + name + ".jar");
    return new DependencyCoordinate(
        "se.kth.depclean.core.analysis",
        name,
        "1.0.0",
        "test",
        jarFile
    );
  }
}
