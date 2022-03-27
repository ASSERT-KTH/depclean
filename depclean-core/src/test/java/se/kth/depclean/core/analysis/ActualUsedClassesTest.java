package se.kth.depclean.core.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static com.google.common.collect.ImmutableSet.of;

import org.junit.jupiter.api.Test;
import se.kth.depclean.core.model.ProjectContext;

class ActualUsedClassesTest implements ProjectContextCreator {

  @Test
  void shouldRegisterClasses() {
    final ProjectContext context = createContext();
    final ActualUsedClasses actualUsedClasses = new ActualUsedClasses(context);
    actualUsedClasses.registerClasses(of(COMMONS_IO_CLASS));

    assertThat(actualUsedClasses.getRegisteredClasses()).containsExactly(COMMONS_IO_CLASS);
  }

  @Test
  void shouldNotRegisterUnknownClasses() {
    final ProjectContext context = createContext();
    final ActualUsedClasses actualUsedClasses = new ActualUsedClasses(context);
    actualUsedClasses.registerClasses(of(UNKNOWN_CLASS));

    assertThat(actualUsedClasses.getRegisteredClasses()).isEmpty();
  }

}