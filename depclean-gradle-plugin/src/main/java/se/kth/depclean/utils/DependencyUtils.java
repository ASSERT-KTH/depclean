package se.kth.depclean.utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DependencyUtils {

  private static final Logger log = LoggerFactory.getLogger(DependencyUtils.class);

  /** A map [artifact] -> [configuration]. */
  private static final Map<ResolvedArtifact, String> ArtifactConfigurationMap = new HashMap<>();

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
   * Get project's resolvable configurations only. This is the Gradle 8+ compatible way to get
   * configurations for dependency analysis. We filter out configurations that are marked as
   * non-resolvable to avoid the "Resolving dependency configuration 'xyz' is not allowed as it is
   * defined as 'canBeResolved=false'" error.
   *
   * @param project Project
   * @return Project's resolvable configurations.
   */
  public Set<Configuration> getResolvableConfigurations(Project project) {
    ConfigurationContainer configurationContainer = project.getConfigurations();

    // Use a very conservative approach - only include configurations we know work
    Set<Configuration> resolvableConfigurations =
        collectResolvableConfigurations(
            project,
            configurationContainer,
            new String[] {
              "compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath"
            },
            "safe");

    // If no safe configurations found, try legacy configurations
    if (resolvableConfigurations.isEmpty()) {
      resolvableConfigurations =
          collectResolvableConfigurations(
              project,
              configurationContainer,
              new String[] {"compile", "runtime", "testCompile", "testRuntime"},
              "legacy");
    }

    project.getLogger().info("Found {} resolvable configurations", resolvableConfigurations.size());
    return resolvableConfigurations;
  }

  private Set<Configuration> collectResolvableConfigurations(
      Project project, ConfigurationContainer container, String[] configNames, String label) {
    Set<Configuration> result = new HashSet<>();
    for (String configName : configNames) {
      Configuration config = container.findByName(configName);
      if (config != null && config.isCanBeResolved()) {
        try {
          // Double-check by trying to access basic metadata
          config.getState();
          result.add(config);
          project.getLogger().debug("Including {} configuration: {}", label, configName);
        } catch (Exception e) {
          project
              .getLogger()
              .debug(
                  "Skipping problematic {} configuration {}: {}",
                  label,
                  configName,
                  e.getMessage());
        }
      }
    }
    return result;
  }

  /**
   * /** Checks if a configuration should be excluded from dependency analysis.
   *
   * @param configName the configuration name
   * @return true if the configuration should be excluded
   */
  private boolean isExcludedConfiguration(String configName) {
    // Exclude configurations that are typically used for publishing/variant selection but not
    // dependency resolution
    return configName.endsWith("Elements")
        || // apiElements, runtimeElements, etc.
        configName.endsWith("Only")
        || // runtimeOnly, compileOnly, etc.
        configName.endsWith("OnlyApiElements")
        || // specific element configurations
        configName.endsWith("OnlyRuntimeElements")
        || configName.equals("default")
        || configName.equals("archives")
        || configName.equals("api")
        || configName.equals("implementation")
        || configName.equals("runtimeOnly")
        || configName.equals("compileOnly")
        || configName.equals("testImplementation")
        || configName.equals("testRuntimeOnly")
        || configName.equals("testCompileOnly")
        || configName.contains("Metadata")
        || configName.contains("Sources")
        || configName.contains("Javadoc")
        || configName.contains("Results")
        || // testResultsElementsForTest, etc.
        configName.startsWith("incrementalAnalysis")
        || configName.contains("Internal");
  }

  /**
   * Returns all the dependencies of the project.
   *
   * @param configurations All the configuration used in the project.
   * @return A set of all dependencies.
   */
  @NonNull
  public Set<ResolvedDependency> getAllDependencies(final Set<Configuration> configurations) {
    Set<ResolvedDependency> allDependencies = new HashSet<>();
    for (Configuration configuration : configurations) {
      // Skip configurations that are not resolvable or known to be problematic
      if (shouldSkipConfiguration(configuration, "")) {
        continue;
      }

      try {
        allDependencies.addAll(
            configuration
                .getResolvedConfiguration()
                .getLenientConfiguration()
                .getAllModuleDependencies());
      } catch (Exception e) {
        // Log the error but continue with other configurations
        log.warn(
            "Warning: Could not resolve dependencies for configuration '{}': {}",
            configuration.getName(),
            e.getMessage());
      }
    }
    return allDependencies;
  }

  /** Logs and returns true when a configuration must be skipped during analysis. */
  private boolean shouldSkipConfiguration(Configuration configuration, String context) {
    String configName = configuration.getName();
    if (!configuration.isCanBeResolved()) {
      log.info("Skipping non-resolvable configuration{}: {}", context, configName);
      return true;
    }
    if (isExcludedConfiguration(configName)) {
      log.info("Skipping excluded configuration{}: {}", context, configName);
      return true;
    }
    return false;
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
   * If there is any dependency which remain unresolved during the analysis, then we should report
   * them.
   *
   * @param configurations All configurations of the project.
   * @return A set of all unresolved dependencies.
   */
  public Set<UnresolvedDependency> getAllUnresolvedDependencies(
      final Set<Configuration> configurations) {
    Set<UnresolvedDependency> allUnresolvedDependencies = new HashSet<>();
    for (Configuration configuration : configurations) {
      try {
        allUnresolvedDependencies.addAll(
            configuration
                .getResolvedConfiguration()
                .getLenientConfiguration()
                .getUnresolvedModuleDependencies());
      } catch (Exception e) {
        // Log the error but continue with other configurations
        log.warn(
            "Warning: Could not get unresolved dependencies for configuration '{}': {}",
            configuration.getName(),
            e.getMessage());
      }
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
  public Set<ResolvedDependency> getDeclaredDependencies(final Set<Configuration> configurations) {
    Set<ResolvedDependency> declaredDependency = new HashSet<>();
    for (Configuration configuration : configurations) {
      // Skip configurations that are not resolvable or known to be problematic
      if (shouldSkipConfiguration(configuration, " in getDeclaredDependencies")) {
        continue;
      }

      try {
        declaredDependency.addAll(
            configuration
                .getResolvedConfiguration()
                .getLenientConfiguration()
                .getFirstLevelModuleDependencies());
      } catch (Exception e) {
        // Log the error but continue with other configurations
        log.warn(
            "Warning: Could not get declared dependencies for configuration '{}': {}",
            configuration.getName(),
            e.getMessage());
      }
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
