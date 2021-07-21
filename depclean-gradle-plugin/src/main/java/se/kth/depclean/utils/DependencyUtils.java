package se.kth.depclean.utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.NonNull;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.UnresolvedDependency;

public class DependencyUtils {

  /**
   * Ctor.
   */
  public DependencyUtils() {
  }

  /**
   * A map [artifact] -> [configuration].
   */
  private static final Map<ResolvedArtifact,
            String> ArtifactConfigurationMap = new HashMap<>();

  /**
   * Getter.
   *
   * @return ArtifactConfigurationMap.
   */
  public Map<ResolvedArtifact, String> getArtifactConfigurationMap() {
    return ArtifactConfigurationMap;
  }

  /**
   * Get project's configuration.
   *
   * @param project Project
   * @return Project's configuration.
   */
  public Set<Configuration> getProjectConfigurations(final Project project) {
    ConfigurationContainer configurationContainer = project.getConfigurations();
    return new HashSet<>(configurationContainer);
  }

  /**
   * Returns all the dependencies of the project.
   *
   * @param configurations All the configuration used in the project.
   * @return A set of all dependencies.
   */
  @NonNull
  public Set<ResolvedDependency> getAllDependencies(
          final Set<Configuration> configurations) {
    Set<ResolvedDependency> allDependencies = new HashSet<>();
    for (Configuration configuration : configurations) {
      allDependencies.addAll(configuration
              .getResolvedConfiguration()
              .getLenientConfiguration()
              .getAllModuleDependencies());
    }
    return allDependencies;
  }

  /**
   * Returns all the artifacts of the project.
   *
   * @param allDependencies All dependencies of the project.
   * @return All artifacts of the project.
   */
  public Set<ResolvedArtifact> getAllArtifacts(final Set<ResolvedDependency> allDependencies) {
    Set<ResolvedArtifact> allArtifacts = new HashSet<>();
    for (ResolvedDependency dependency : allDependencies) {
      Set<ResolvedArtifact> partialAllArtifacts = new HashSet<>(dependency.getModuleArtifacts());
      for (ResolvedArtifact artifact : partialAllArtifacts) {
        ArtifactConfigurationMap.put(artifact, dependency.getConfiguration());
        allArtifacts.add(artifact);
      }
    }
    return allArtifacts;
  }

  /**
   * If there is any dependency which remain unresolved during the analysis,
   * then we should report them.
   *
   * @param configurations All configurations of the project.
   * @return A set of all unresolved dependencies.
   */
  public Set<UnresolvedDependency> getAllUnresolvedDependencies(
          final Set<Configuration> configurations) {
    Set<UnresolvedDependency> allUnresolvedDependencies = new HashSet<>();
    for (Configuration configuration : configurations) {
      allUnresolvedDependencies.addAll(configuration
              .getResolvedConfiguration()
              .getLenientConfiguration()
              .getUnresolvedModuleDependencies());
    }
    return allUnresolvedDependencies;
  }

  /**
   * Returns all the dependencies of the project.
   *
   * @param configurations All the configuration used in the project.
   * @return A set of all dependencies.
   */
  @NonNull
  public Set<ResolvedDependency> getDeclaredDependencies(
          final Set<Configuration> configurations) {
    Set<ResolvedDependency> declaredDependency = new HashSet<>();
    for (Configuration configuration : configurations) {
      declaredDependency.addAll(configuration
              .getResolvedConfiguration()
              .getLenientConfiguration()
              .getFirstLevelModuleDependencies());
    }
    return declaredDependency;
  }

  /**
   * To get the artifacts which are declared in the project.
   *
   * @param declaredDependency Project's configuration.
   * @return A set of declared artifacts.
   */
  public Set<ResolvedArtifact> getDeclaredArtifacts(
          final Set<ResolvedDependency> declaredDependency) {
    Set<ResolvedArtifact> declaredArtifacts = new HashSet<>();
    for (ResolvedDependency dependency : declaredDependency) {
      declaredArtifacts.addAll(dependency.getModuleArtifacts());
    }
    return declaredArtifacts;
  }

}
