package se.kth.depclean.core.analysis.model;

import static org.assertj.core.api.Assertions.assertThat;
import static se.kth.depclean.core.analysis.ProjectContextCreator.createDependency;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;
import se.kth.depclean.core.analysis.ProjectContextCreator;

class ProjectDependencyAnalysisTest implements ProjectContextCreator {

  @Test
  void shouldBuildResultingDependencyGraph() {
    final ProjectDependencyAnalysis analysis =
        new ProjectDependencyAnalysis(
            ImmutableSet.of(COMMONS_IO_DEPENDENCY),
            ImmutableSet.of(JUNIT_DEPENDENCY),
            ImmutableSet.of(),
            ImmutableSet.of(COMMONS_LANG_DEPENDENCY),
            ImmutableSet.of(COMMONS_MATH_DEPENDENCY),
            ImmutableSet.of(COMMONS_IO_DEPENDENCY),
            ImmutableSet.of(COMMONS_LOGGING_DEPENDENCY),
            ImmutableSet.of(),
            ImmutableSet.of(),
            ImmutableMap.of(),
            new TestDependencyGraph(
                createDependency("ExampleClass"),
                ImmutableSet.of(COMMONS_IO_DEPENDENCY),
                ImmutableSet.of(COMMONS_LANG_DEPENDENCY),
                ImmutableSet.of(COMMONS_MATH_DEPENDENCY),
                ImmutableSet.of(JUNIT_DEPENDENCY, COMMONS_LOGGING_DEPENDENCY)));

    assertThat(analysis.getUsedDependencies())
        .containsExactlyInAnyOrder(
            new DebloatedDependency(
                COMMONS_IO_DEPENDENCY, ImmutableSet.of(COMMONS_LOGGING_DEPENDENCY)),
            new DebloatedDependency(
                COMMONS_LANG_DEPENDENCY, ImmutableSet.of(COMMONS_LOGGING_DEPENDENCY)),
            new DebloatedDependency(JUNIT_DEPENDENCY, ImmutableSet.of()));
  }
}
