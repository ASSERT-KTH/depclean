package se.kth.depclean.core.analysis;

import com.google.common.collect.ImmutableSet;
import org.apache.maven.artifact.Artifact;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class DeclaredDependencyGraphTest implements ArtifactCreator {

  @Test
  void shouldContainArtifactWithClasses() throws IOException {
    final Artifact artifact = createArtifact("ExampleClass");

    final DeclaredDependencyGraph graph = new DeclaredDependencyGraph(ImmutableSet.of(artifact),
        ImmutableSet.of(artifact));
    assertThat(graph.getClassesForArtifact(artifact)).containsExactly("ExampleClass");
  }

  @Test
  void shouldContainClassWithArtifacts() throws IOException {
    final Artifact artifact = createArtifact("ExampleClass");

    final DeclaredDependencyGraph graph = new DeclaredDependencyGraph(ImmutableSet.of(artifact),
        ImmutableSet.of(artifact));
    assertThat(graph.getArtifactsForClass("ExampleClass")).containsExactly(artifact);
  }
}