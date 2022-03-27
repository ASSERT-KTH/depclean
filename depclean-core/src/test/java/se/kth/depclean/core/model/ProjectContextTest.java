package se.kth.depclean.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import se.kth.depclean.core.analysis.ProjectContextCreator;
import se.kth.depclean.core.model.ProjectContext;

class ProjectContextTest implements ProjectContextCreator {

  @Test
  void shouldContainDependenciesWithClasses() {
    final ProjectContext context = createContext();
    assertThat(context.getClassesForDependency(COMMONS_IO_DEPENDENCY)).hasSize(123);
    assertThat(context.getClassesForDependency(COMMONS_LANG_DEPENDENCY)).hasSize(127);
    assertThat(context.getClassesForDependency(COMMONS_LOGGING_DEPENDENCY)).hasSize(20);
    assertThat(context.getClassesForDependency(JUNIT_DEPENDENCY)).hasSize(113);
    assertThat(context.getClassesForDependency(UNKNOWN_DEPENDENCY)).isEmpty();
  }

  @Test
  void shouldContainClassWithDependencies() {
    final ProjectContext context = createContext();
    assertThat(context.getDependenciesForClass(COMMONS_IO_CLASS)).hasSize(1);
    assertThat(context.getDependenciesForClass(JUNIT_CLASS)).hasSize(1);
    assertThat(context.getDependenciesForClass(UNKNOWN_CLASS)).isEmpty();
  }

  @Test
  void shouldIgnoreTestDependencies() {
    final ProjectContext context = createContextIgnoringTests();
    assertThat(context.getDependenciesForClass(COMMONS_IO_CLASS)).hasSize(1);
    assertThat(context.getDependenciesForClass(JUNIT_CLASS)).isEmpty();
    assertThat(context.getDependenciesForClass(UNKNOWN_CLASS)).isEmpty();
  }
}
