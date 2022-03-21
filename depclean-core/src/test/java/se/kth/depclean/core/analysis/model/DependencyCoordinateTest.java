package se.kth.depclean.core.analysis.model;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import se.kth.depclean.core.analysis.ProjectContextCreator;

class DependencyCoordinateTest {

  @Test
  void shouldGetRelatedClasses() {
    final DependencyCoordinate dependency = ProjectContextCreator.COMMONS_IO_DEPENDENCY;

    assertThat(dependency.getRelatedClasses()).hasSize(123);
  }
}
