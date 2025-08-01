package se.kth.depclean.core.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;
import se.kth.depclean.core.model.ProjectContext;

class ProjectDependencyAnalysisBuilderTest implements ProjectContextCreator {

  @Test
  void shouldFindOneUsedDirectDependency() {
    final ProjectContext context = createContext();
    final ActualUsedClasses actualUsedClasses = new ActualUsedClasses(context);
    actualUsedClasses.registerClasses(ImmutableSet.of(COMMONS_IO_CLASS));
    final ProjectDependencyAnalysisBuilder analysisBuilder =
        new ProjectDependencyAnalysisBuilder(context, actualUsedClasses);

    assertThat(analysisBuilder.analyse().getUsedDirectDependencies())
        .containsExactlyInAnyOrder(COMMONS_IO_DEPENDENCY);
  }

  @Test
  void shouldFindUsedInheritedDependencies() {
    final ProjectContext context = createContext();
    final ActualUsedClasses actualUsedClasses = new ActualUsedClasses(context);
    actualUsedClasses.registerClasses(ImmutableSet.of(COMMONS_LANG_CLASS));
    final ProjectDependencyAnalysisBuilder analysisBuilder =
        new ProjectDependencyAnalysisBuilder(context, actualUsedClasses);

    assertThat(analysisBuilder.analyse().getUsedInheritedDirectDependencies())
        .containsExactlyInAnyOrder(COMMONS_LANG_DEPENDENCY);
  }

  @Test
  void shouldFindUsedTransitiveDependencies() {
    final ProjectContext context = createContext();
    final ActualUsedClasses actualUsedClasses = new ActualUsedClasses(context);
    actualUsedClasses.registerClasses(ImmutableSet.of(COMMONS_LOGGING_CLASS));
    final ProjectDependencyAnalysisBuilder analysisBuilder =
        new ProjectDependencyAnalysisBuilder(context, actualUsedClasses);

    assertThat(analysisBuilder.analyse().getUsedTransitiveDependencies())
        .containsExactlyInAnyOrder(COMMONS_LOGGING_DEPENDENCY);
  }

  @Test
  void shouldFindUnusedDirectDependencies() {
    final ProjectContext context = createContext();
    final ActualUsedClasses actualUsedClasses = new ActualUsedClasses(context);
    final ProjectDependencyAnalysisBuilder analysisBuilder =
        new ProjectDependencyAnalysisBuilder(context, actualUsedClasses);

    assertThat(analysisBuilder.analyse().getUnusedDirectDependencies())
        .containsExactlyInAnyOrder(COMMONS_IO_DEPENDENCY, JUNIT_DEPENDENCY);
  }

  @Test
  void shouldFindUnusedTransitiveDependencies() {
    final ProjectContext context = createContext();
    final ActualUsedClasses actualUsedClasses = new ActualUsedClasses(context);
    final ProjectDependencyAnalysisBuilder analysisBuilder =
        new ProjectDependencyAnalysisBuilder(context, actualUsedClasses);

    assertThat(analysisBuilder.analyse().getUnusedTransitiveDependencies())
        .containsExactlyInAnyOrder(COMMONS_LOGGING_DEPENDENCY);
  }

  @Test
  void shouldFindUnusedInheritedDependencies() {
    final ProjectContext context = createContext();
    final ActualUsedClasses actualUsedClasses = new ActualUsedClasses(context);
    final ProjectDependencyAnalysisBuilder analysisBuilder =
        new ProjectDependencyAnalysisBuilder(context, actualUsedClasses);

    assertThat(analysisBuilder.analyse().getUnusedInheritedDirectDependencies())
        .containsExactlyInAnyOrder(COMMONS_LANG_DEPENDENCY);
  }

  @Test
  void shouldIgnoreDependency() {
    final ProjectContext context = createContextIgnoringDependency();
    final ActualUsedClasses actualUsedClasses = new ActualUsedClasses(context);
    final ProjectDependencyAnalysisBuilder analysisBuilder =
        new ProjectDependencyAnalysisBuilder(context, actualUsedClasses);

    assertThat(analysisBuilder.analyse().getUsedDirectDependencies())
        .containsExactlyInAnyOrder(COMMONS_IO_DEPENDENCY);
    assertThat(analysisBuilder.analyse().getUnusedDirectDependencies())
        .doesNotContain(COMMONS_IO_DEPENDENCY);
  }

  @Test
  void shouldHaveRightStatus() {
    final ProjectContext context = createContextIgnoringDependency();
    final ActualUsedClasses actualUsedClasses = new ActualUsedClasses(context);
    actualUsedClasses.registerClasses(ImmutableSet.of(COMMONS_IO_CLASS));
    final ProjectDependencyAnalysisBuilder analysisBuilder =
        new ProjectDependencyAnalysisBuilder(context, actualUsedClasses);

    assertThat(analysisBuilder.analyse().getDependencyInfo(COMMONS_IO_DEPENDENCY.toString()))
        .isNotNull()
        .extracting("status")
        .isEqualTo("used");
    assertThat(analysisBuilder.analyse().getDependencyInfo(COMMONS_LANG_DEPENDENCY.toString()))
        .isNotNull()
        .extracting("status")
        .isEqualTo("bloated");
  }

  @Test
  void shouldHaveRightType() {
    final ProjectContext context = createContextIgnoringDependency();
    final ActualUsedClasses actualUsedClasses = new ActualUsedClasses(context);
    actualUsedClasses.registerClasses(ImmutableSet.of(COMMONS_IO_CLASS));
    final ProjectDependencyAnalysisBuilder analysisBuilder =
        new ProjectDependencyAnalysisBuilder(context, actualUsedClasses);

    assertThat(analysisBuilder.analyse().getDependencyInfo(COMMONS_IO_DEPENDENCY.toString()))
        .isNotNull()
        .extracting("type")
        .isEqualTo("direct");
    assertThat(analysisBuilder.analyse().getDependencyInfo(COMMONS_LANG_DEPENDENCY.toString()))
        .isNotNull()
        .extracting("type")
        .isEqualTo("inherited");
    assertThat(analysisBuilder.analyse().getDependencyInfo(COMMONS_LOGGING_DEPENDENCY.toString()))
        .isNotNull()
        .extracting("type")
        .isEqualTo("transitive");
  }

  @Test
  void shouldBuildDependencyClassesMap() {
    final ProjectContext context = createContext();
    final ActualUsedClasses actualUsedClasses = new ActualUsedClasses(context);
    actualUsedClasses.registerClasses(ImmutableSet.of(COMMONS_IO_CLASS));
    final ProjectDependencyAnalysisBuilder analysisBuilder =
        new ProjectDependencyAnalysisBuilder(context, actualUsedClasses);

    assertThat(analysisBuilder.analyse().getDependencyClassesMap())
        .hasEntrySatisfying(
            COMMONS_IO_DEPENDENCY,
            dependencyTypes -> {
              assertThat(dependencyTypes.getAllTypes()).hasSize(123);
              assertThat(dependencyTypes.getUsedTypes()).hasSize(1);
            })
        .hasEntrySatisfying(
            COMMONS_LANG_DEPENDENCY,
            dependencyTypes -> {
              assertThat(dependencyTypes.getAllTypes()).hasSize(345);
              assertThat(dependencyTypes.getUsedTypes()).isEmpty();
            })
        .hasEntrySatisfying(
            COMMONS_LOGGING_DEPENDENCY,
            dependencyTypes -> {
              assertThat(dependencyTypes.getAllTypes()).hasSize(20);
              assertThat(dependencyTypes.getUsedTypes()).isEmpty();
            });
  }
}
