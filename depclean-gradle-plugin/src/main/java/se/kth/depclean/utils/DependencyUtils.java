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
   * Get project's resolvable configurations only.
   * This is the Gradle 8+ compatible way to get configurations for dependency analysis.
   * We filter out configurations that are marked as non-resolvable to avoid the
   * "Resolving dependency configuration 'xyz' is not allowed as it is defined as 'canBeResolved=false'" error.
   *
   * @param project Project
   * @return Project's resolvable configurations.
   */
  public Set<Configuration> getResolvableConfigurations(Project project) {
    ConfigurationContainer configurationContainer = project.getConfigurations();
    Set<Configuration> resolvableConfigurations = new HashSet<>();

    // Use a very conservative approach - only include configurations we know work
    String[] safeConfigurationNames = {
        "compileClasspath", 
        "runtimeClasspath", 
        "testCompileClasspath", 
        "testRuntimeClasspath"
    };

    for (String configName : safeConfigurationNames) {
      Configuration config = configurationContainer.findByName(configName);
      if (config != null && config.isCanBeResolved()) {
        try {
          // Double-check by trying to access basic metadata
          config.getState();
          resolvableConfigurations.add(config);
          project.getLogger().debug("Including safe configuration: {}", configName);
        } catch (Exception e) {
          project.getLogger().debug("Skipping problematic safe configuration {}: {}", configName, e.getMessage());
        }
      }
    }

    // If no safe configurations found, try legacy configurations
    if (resolvableConfigurations.isEmpty()) {
      String[] legacyConfigurationNames = {"compile", "runtime", "testCompile", "testRuntime"};
      for (String configName : legacyConfigurationNames) {
        Configuration config = configurationContainer.findByName(configName);
        if (config != null && config.isCanBeResolved()) {
          try {
            config.getState();
            resolvableConfigurations.add(config);
            project.getLogger().debug("Including legacy configuration: {}", configName);
          } catch (Exception e) {
            project.getLogger().debug("Skipping problematic legacy configuration {}: {}", configName, e.getMessage());
          }
        }
      }
    }

    project.getLogger().info("Found {} resolvable configurations", resolvableConfigurations.size());
    return resolvableConfigurations;
  }

  /**
  /**
   * Checks if a configuration should be excluded from dependency analysis.
   * 
   * @param configName the configuration name
   * @return true if the configuration should be excluded
   */
  private boolean isExcludedConfiguration(String configName) {
    // Exclude configurations that are typically used for publishing/variant selection but not dependency resolution
    return configName.endsWith("Elements") ||           // apiElements, runtimeElements, etc.
           configName.endsWith("Only") ||               // runtimeOnly, compileOnly, etc. 
           configName.endsWith("OnlyApiElements") ||     // specific element configurations
           configName.endsWith("OnlyRuntimeElements") || 
           configName.equals("default") ||
           configName.equals("archives") ||
           configName.equals("api") ||
           configName.equals("implementation") ||
           configName.equals("runtimeOnly") ||
           configName.equals("compileOnly") ||
           configName.equals("testImplementation") ||
           configName.equals("testRuntimeOnly") ||
           configName.equals("testCompileOnly") ||
           configName.contains("Metadata") ||
           configName.contains("Sources") ||
           configName.contains("Javadoc") ||
           configName.contains("Results") ||             // testResultsElementsForTest, etc.
           configName.startsWith("incrementalAnalysis") ||
           configName.contains("Internal");
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
      String configName = configuration.getName();
      
      // Skip configurations that are not resolvable
      if (!configuration.isCanBeResolved()) {
        System.out.println("Skipping non-resolvable configuration: " + configName);
        continue;
      }
      
      // Skip configurations that are known to be problematic
      if (isExcludedConfiguration(configName)) {
        System.out.println("Skipping excluded configuration: " + configName);
        continue;
      }
      
      try {
        allDependencies.addAll(configuration
            .getResolvedConfiguration()
            .getLenientConfiguration()
            .getAllModuleDependencies());
      } catch (Exception e) {
        // Log the error but continue with other configurations
        System.out.println("Warning: Could not resolve dependencies for configuration '" + 
                          configName + "': " + e.getMessage());
      }
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
      try {
        allUnresolvedDependencies.addAll(configuration
            .getResolvedConfiguration()
            .getLenientConfiguration()
            .getUnresolvedModuleDependencies());
      } catch (Exception e) {
        // Log the error but continue with other configurations
        System.out.println("Warning: Could not get unresolved dependencies for configuration '" + 
                          configuration.getName() + "': " + e.getMessage());
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
  public Set<ResolvedDependency> getDeclaredDependencies(
      final Set<Configuration> configurations) {
    Set<ResolvedDependency> declaredDependency = new HashSet<>();
    for (Configuration configuration : configurations) {
      String configName = configuration.getName();
      
      // Skip configurations that are not resolvable
      if (!configuration.isCanBeResolved()) {
        System.out.println("Skipping non-resolvable configuration in getDeclaredDependencies: " + configName);
        continue;
      }
      
      // Skip configurations that are known to be problematic
      if (isExcludedConfiguration(configName)) {
        System.out.println("Skipping excluded configuration in getDeclaredDependencies: " + configName);
        continue;
      }
      
      try {
        declaredDependency.addAll(configuration
            .getResolvedConfiguration()
            .getLenientConfiguration()
            .getFirstLevelModuleDependencies());
      } catch (Exception e) {
        // Log the error but continue with other configurations
        System.out.println("Warning: Could not get declared dependencies for configuration '" + 
                          configName + "': " + e.getMessage());
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
