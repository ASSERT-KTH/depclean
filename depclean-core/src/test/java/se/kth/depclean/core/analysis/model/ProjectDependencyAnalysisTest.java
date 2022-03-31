package se.kth.depclean.core.analysis.model;

import static com.google.common.collect.ImmutableSet.of;
import static org.assertj.core.api.Assertions.assertThat;
import se.kth.depclean.core.analysis.ProjectContextCreator;
import static se.kth.depclean.core.analysis.ProjectContextCreator.createDependency;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import se.kth.depclean.core.analysis.model.DebloatedDependency;
import se.kth.depclean.core.analysis.model.ProjectDependencyAnalysis;

class ProjectDependencyAnalysisTest implements ProjectContextCreator {

  @Test
  void shouldBuildResultingDependencyGraph() {
    final ProjectDependencyAnalysis analysis = new ProjectDependencyAnalysis(
        of(COMMONS_IO_DEPENDENCY),
        of(JUNIT_DEPENDENCY),
        of(),
        of(COMMONS_LANG_DEPENDENCY),
        of(COMMONS_LOGGING_DEPENDENCY),
        of(),
        of(),
        ImmutableMap.of(),
        new TestDependencyGraph(
            createDependency("ExampleClass"),
            of(COMMONS_IO_DEPENDENCY),
            of(COMMONS_LANG_DEPENDENCY),
            of(JUNIT_DEPENDENCY, COMMONS_LOGGING_DEPENDENCY)
        )
    );

    assertThat(analysis.getDebloatedDependencies())
        .containsExactlyInAnyOrder(
            new DebloatedDependency(COMMONS_IO_DEPENDENCY, of(COMMONS_LOGGING_DEPENDENCY)),
            new DebloatedDependency(JUNIT_DEPENDENCY, of())
        );
  }
}