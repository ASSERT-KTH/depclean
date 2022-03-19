package se.kth.depclean.core.analysis;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.apache.maven.artifact.Artifact;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.google.common.collect.ImmutableSet.of;
import static org.assertj.core.api.Assertions.assertThat;

class ActualUsedClassesTest implements ArtifactCreator {
  private final Artifact exampleArtifact = createArtifact("ExampleClass");
  private final ImmutableSet<Artifact> allArtifacts = of(exampleArtifact);

  @Test
  void shouldRegisterClasses() throws IOException {
    final DeclaredDependencyGraph declaredDependencyGraph =
        new DeclaredDependencyGraph(allArtifacts, of());
    final ActualUsedClasses actualUsedClasses = new ActualUsedClasses(declaredDependencyGraph);
    actualUsedClasses.registerClasses(Sets.newHashSet("ExampleClass.class"));

    assertThat(actualUsedClasses.getRegisteredClasses()).containsExactly("ExampleClass");
  }

  @Test
  void shouldNotRegisterUnknownClasses() throws IOException {
    final DeclaredDependencyGraph declaredDependencyGraph =
        new DeclaredDependencyGraph(allArtifacts, of());
    final ActualUsedClasses actualUsedClasses = new ActualUsedClasses(declaredDependencyGraph);
    actualUsedClasses.registerClasses(Sets.newHashSet("Unknown.class"));

    assertThat(actualUsedClasses.getRegisteredClasses()).isEmpty();
  }

}