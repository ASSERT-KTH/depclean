package se.kth.depclean.core.analysis;

import com.google.common.collect.ImmutableSet;
import org.apache.maven.artifact.Artifact;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.google.common.collect.ImmutableSet.of;
import static org.assertj.core.api.Assertions.assertThat;

class ProjectDependencyAnalysisBuilderTest implements ArtifactCreator {
  private final Artifact commonsIoArtifact = createArtifact("commons-io");
  private final Artifact commonsLangArtifact = createArtifact("commons-lang");
  private final Artifact commonsLoggingArtifact = createArtifact("commons-logging-api");
  private final ImmutableSet<Artifact> allArtifacts =
      of(commonsIoArtifact, commonsLangArtifact, commonsLoggingArtifact);

  @Test
  void shouldFindOneUsedDirectDependency() throws IOException {
    final DeclaredDependencyGraph declaredDependencyGraph =
        new DeclaredDependencyGraph(allArtifacts, of(commonsIoArtifact));
    final ActualUsedClasses actualUsedClasses = new ActualUsedClasses(declaredDependencyGraph);
    actualUsedClasses.registerClasses(of("org.apache.commons.io.IOUtils"));
    final ProjectDependencyAnalysisBuilder analysisBuilder =
        new ProjectDependencyAnalysisBuilder(declaredDependencyGraph, actualUsedClasses);

    assertThat(analysisBuilder.analyse().getUsedDirectArtifacts()).containsExactly(commonsIoArtifact);
  }

  @Test
  void shouldFindUsedTransitiveDependencies() throws IOException {
    final DeclaredDependencyGraph declaredDependencyGraph =
        new DeclaredDependencyGraph(allArtifacts, of(commonsLangArtifact));
    final ActualUsedClasses actualUsedClasses = new ActualUsedClasses(declaredDependencyGraph);
    actualUsedClasses.registerClasses(of("org.apache.commons.io.IOUtils"));
    final ProjectDependencyAnalysisBuilder analysisBuilder =
        new ProjectDependencyAnalysisBuilder(declaredDependencyGraph, actualUsedClasses);

    assertThat(analysisBuilder.analyse().getUsedTransitiveArtifacts()).containsExactly(commonsIoArtifact);
  }

  @Test
  void shouldFindUnusedDirectDependencies() throws IOException {
    final DeclaredDependencyGraph declaredDependencyGraph =
        new DeclaredDependencyGraph(allArtifacts, of(commonsLangArtifact, commonsLoggingArtifact));
    final ActualUsedClasses actualUsedClasses = new ActualUsedClasses(declaredDependencyGraph);
    actualUsedClasses.registerClasses(of("org.apache.commons.io.IOUtils"));
    final ProjectDependencyAnalysisBuilder analysisBuilder =
        new ProjectDependencyAnalysisBuilder(declaredDependencyGraph, actualUsedClasses);

    assertThat(analysisBuilder.analyse().getUnusedDirectArtifacts())
        .containsExactlyInAnyOrder(commonsLoggingArtifact, commonsLangArtifact);
  }

  @Test
  void shouldBuildArtifactClassesMap() throws IOException {
    final DeclaredDependencyGraph declaredDependencyGraph =
        new DeclaredDependencyGraph(allArtifacts, of(commonsLangArtifact));
    final ActualUsedClasses actualUsedClasses = new ActualUsedClasses(declaredDependencyGraph);
    actualUsedClasses.registerClasses(of("org.apache.commons.io.IOUtils"));
    final ProjectDependencyAnalysisBuilder analysisBuilder =
        new ProjectDependencyAnalysisBuilder(declaredDependencyGraph, actualUsedClasses);

    assertThat(analysisBuilder.analyse().getArtifactClassesMap())
        .hasEntrySatisfying(commonsIoArtifact.toString(), artifactTypes -> {
          assertThat(artifactTypes.getAllTypes()).hasSize(123);
          assertThat(artifactTypes.getUsedTypes()).hasSize(1);
        })
        .hasEntrySatisfying(commonsLangArtifact.toString(), artifactTypes -> {
          assertThat(artifactTypes.getAllTypes()).hasSize(127);
          assertThat(artifactTypes.getUsedTypes()).hasSize(0);
        })
        .hasEntrySatisfying(commonsLoggingArtifact.toString(), artifactTypes -> {
          assertThat(artifactTypes.getAllTypes()).hasSize(20);
          assertThat(artifactTypes.getUsedTypes()).hasSize(0);
        });
  }

}