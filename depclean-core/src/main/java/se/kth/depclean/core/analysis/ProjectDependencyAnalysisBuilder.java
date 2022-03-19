package se.kth.depclean.core.analysis;

import static com.google.common.collect.Sets.newHashSet;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.artifact.Artifact;

/**
 * Builds the analysis given the declared dependencies and the one actually used.
 */
@Slf4j
public class ProjectDependencyAnalysisBuilder {

  private final DeclaredDependencyGraph declaredDependencyGraph;
  private final ActualUsedClasses actualUsedClasses;
  private final Set<Artifact> usedArtifacts;

  ProjectDependencyAnalysisBuilder(DeclaredDependencyGraph declaredDependencyGraph,
                                   ActualUsedClasses actualUsedClasses) {
    this.declaredDependencyGraph = declaredDependencyGraph;
    this.actualUsedClasses = actualUsedClasses;
    usedArtifacts = actualUsedClasses.getRegisteredClasses().stream()
        .flatMap(clazz -> declaredDependencyGraph.getArtifactsForClass(clazz).stream())
        .collect(Collectors.toSet());
  }

  /**
   * Analyse the dependencies to find out what is used and what is not.
   *
   * @return the analysis
   */
  public ProjectDependencyAnalysis analyse() {
    final Set<Artifact> usedDirectDependencyArtifacts = getUsedDirectDependencyArtifacts();
    final Set<Artifact> usedTransitiveDependencyArtifacts = getUsedTransitiveDependencyArtifacts();
    final Set<Artifact> unusedDirectArtifacts = newHashSet(declaredDependencyGraph.getDirectDependencyArtifacts());
    unusedDirectArtifacts.removeAll(usedDirectDependencyArtifacts);
    final Map<String, ArtifactTypes> artifactClassesMap = buildArtifactClassesMap();

    return new ProjectDependencyAnalysis(
        usedDirectDependencyArtifacts,
        usedTransitiveDependencyArtifacts,
        unusedDirectArtifacts,
        artifactClassesMap);
  }

  private Map<String, ArtifactTypes> buildArtifactClassesMap() {
    final Map<String, ArtifactTypes> output = new HashMap<>();
    for (Artifact artifact : declaredDependencyGraph.getAllArtifacts()) {
      final Set<String> allClasses = declaredDependencyGraph.getClassesForArtifact(artifact);
      final Set<String> usedClasses = newHashSet(allClasses);
      usedClasses.retainAll(actualUsedClasses.getRegisteredClasses());
      output.put(artifact.toString(), new ArtifactTypes(allClasses, usedClasses));
    }
    return output;
  }

  private Set<Artifact> getUsedTransitiveDependencyArtifacts() {
    return usedArtifacts.stream()
        .filter(a -> !declaredDependencyGraph.getDirectDependencyArtifacts().contains(a))
        .peek(artifact -> log.info("## Used Transitive dependency {}", artifact))
        .collect(Collectors.toSet());
  }

  private Set<Artifact> getUsedDirectDependencyArtifacts() {
    return usedArtifacts.stream()
        .filter(a -> declaredDependencyGraph.getDirectDependencyArtifacts().contains(a))
        .peek(artifact -> log.info("## Used Direct dependency {}", artifact))
        .collect(Collectors.toSet());
  }
}
