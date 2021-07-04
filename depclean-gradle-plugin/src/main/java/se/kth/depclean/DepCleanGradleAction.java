package se.kth.depclean;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.logging.Logger;
import org.jetbrains.annotations.NotNull;
import lombok.NonNull;
import lombok.SneakyThrows;
import se.kth.depclean.analysis.DefaultGradleProjectDependencyAnalyzer;
import se.kth.depclean.analysis.GradleProjectDependencyAnalysis;
import se.kth.depclean.core.analysis.ProjectDependencyAnalyzerException;
import se.kth.depclean.util.JarUtils;

/**
 * Depclean default and only action.
 */
public class DepCleanGradleAction implements Action<Project> {

  // To get some clear visible results.
  private static final String SEPARATOR = "-------------------------------------------------------";

  // To reduce some code(Frequent use)
  private static final String sep = File.separator;

  public final Map<ResolvedArtifact, String> artifactConfigurationMap = new HashMap<>();

  @SneakyThrows
  @Override
  public void execute(@NotNull Project project) {

    Logger logger = project.getLogger();

    DepCleanGradlePluginExtension extension = project.getExtensions().getByType(DepCleanGradlePluginExtension.class);
    boolean isIgnoreTest = extension.isIgnoreTest();
    boolean failIfUnusedDirect = extension.isFailIfUnusedDirect();
    boolean failIfUnusedTransitive = extension.isFailIfUnusedTransitive();
    boolean failIfUnusedInherited = extension.isFailIfUnusedInherited();
    List<String> ignoreConfiguration = extension.getIgnoreConfiguration();
    Set<String> ignoreDependencies = extension.getIgnoreDependency();

    logger.lifecycle("Starting DepClean dependency analysis");

    // Path to the project directory.
    final String projectDirPath = project.getProjectDir().getAbsolutePath() + sep;

    // Path to the dependency directory.
    final String dependencyDirPath = projectDirPath + "build" + sep + "Dependency";

    // Path to the libs directory.
    final String libsDirPath = projectDirPath + "build" + sep + "libs";

    // Path to the build classes directory.
    final String classesDirPath = projectDirPath + "build" + sep + "classes";

    // Project's configurations.
    Set<Configuration> configurations = getProjectConfigurations(project);

    // Setting can be resolved to true to get transitive dependencies of the project's configuration.
    // Also, it is mandatory to change this parameter before runtime.
    configurations.stream().iterator()
            .forEachRemaining(configuration -> configuration.setCanBeResolved(true));

    // All resolved dependencies including transitive ones of the project.
    Set<ResolvedDependency> allDependencies = getAllDependencies(configurations);

    // all resolved artifacts of this project
    Set<ResolvedArtifact> allArtifacts = new HashSet<>();
    for (ResolvedDependency dependency : allDependencies) {
      Set<ResolvedArtifact> partialAllArtifacts = new HashSet<>(dependency.getModuleArtifacts());
      for (ResolvedArtifact artifact : partialAllArtifacts) {
        artifactConfigurationMap.put(artifact, dependency.getConfiguration());
        allArtifacts.add(artifact);
      }
    }

    // all unresolved dependencies including transitive ones of the project.
    Set<UnresolvedDependency> allUnresolvedDependencies = getAllUnresolvedDependencies(configurations);

    // All declared dependencies of the project.
    Set<ResolvedDependency> declaredDependencies = getDeclaredDependencies(configurations);

    // All declared artifacts of the project.
    Set<ResolvedArtifact> declaredArtifacts = getDeclaredArtifacts(declaredDependencies);


    Set<String> declaredArtifactsGroupArtifactIds = new HashSet<>();
    for (ResolvedArtifact artifact : declaredArtifacts) {
      String name = getName(artifact, ignoreConfiguration);
      if (name != null) {
        declaredArtifactsGroupArtifactIds.add(name);
      }
    }

    // Copying dependencies locally to get their size.
    File dependencyDirectory = new File(dependencyDirPath);
    for (ResolvedArtifact artifact : allArtifacts) {
      // copying jar files directly from the user's .m2 directory
      File jarFile = artifact.getFile();
      if (jarFile.getAbsolutePath().endsWith(".jar")) {
        try {
          FileUtils.copyFileToDirectory(jarFile, dependencyDirectory);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }

    // Copying files from libs directory to dependency directory.
    if (new File(libsDirPath).exists()) {
      try {
        FileUtils.copyDirectory(new File(libsDirPath), new File(dependencyDirPath));
      } catch (IOException | NullPointerException e) {
        logger.error("Error copying directory libs to dependency");
      }
    }

    /* Get the size of all the dependencies */
    Map<String, Long> sizeOfDependencies = new HashMap<>();

    // First, add the size of the project, as the sum of all the files in target/classes
    String projectJar = project.getName() + "-" + project.getVersion() + ".jar";
    long projectSize = FileUtils.sizeOf(new File(classesDirPath));
    sizeOfDependencies.put(projectJar, projectSize);

    // Now adding the size of all the files one by one from the dependency directory (build/Dependency).
    if (Files.exists(Path.of(String.valueOf(Paths.get(
            dependencyDirPath))))) {
      Iterator<File> iterator = FileUtils.iterateFiles(
              new File(
                      dependencyDirPath), new String[]{"jar"}, true);
      while (iterator.hasNext()) {
        File file = iterator.next();
        sizeOfDependencies.put(file.getName(), FileUtils.sizeOf(file));
      }
    } else {
      logger.warn("Dependencies where not copied locally");
    }

    /* Decompress dependencies */
    if (dependencyDirectory.exists()) {
      JarUtils.decompressJars(dependencyDirPath);
    } else {
      printString("Unable to decompress jars at " + dependencyDirPath);
    }

    /* Analyze dependencies usage status */
    GradleProjectDependencyAnalysis projectDependencyAnalysis = null;
    DefaultGradleProjectDependencyAnalyzer dependencyAnalyzer = new DefaultGradleProjectDependencyAnalyzer(isIgnoreTest);
    try {
      projectDependencyAnalysis = dependencyAnalyzer.analyze(project);
    } catch (ProjectDependencyAnalyzerException e) {
      logger.error("Unable to analyze dependencies.");
    }

    // Collecting the dependencies in their respective categories after the dependency analysis has been completed.
    assert projectDependencyAnalysis != null;
    Set<ResolvedArtifact> usedTransitiveArtifacts = projectDependencyAnalysis.getUsedUndeclaredArtifacts();
    Set<ResolvedArtifact> usedDirectArtifacts = projectDependencyAnalysis.getUsedDeclaredArtifacts();
    Set<ResolvedArtifact> unusedDirectArtifacts = projectDependencyAnalysis.getUnusedDeclaredArtifacts();
    Set<ResolvedArtifact> unusedTransitiveArtifacts = new HashSet<>(allArtifacts);

    // --- used dependencies
    Set<String> usedDirectArtifactsCoordinates = new HashSet<>();
    Set<String> usedInheritedArtifactsCoordinates = new HashSet<>();
    Set<String> usedTransitiveArtifactsCoordinates = new HashSet<>();

    for (ResolvedArtifact artifact : usedDirectArtifacts) {
      String artifactGroupArtifactIds = getName(artifact, ignoreConfiguration);
      if (artifactGroupArtifactIds == null) continue;
      if (declaredArtifactsGroupArtifactIds.contains(artifactGroupArtifactIds)) {
        // the artifact is declared in the build file
        usedDirectArtifactsCoordinates.add(artifactGroupArtifactIds);
      } else {
        // the artifact is inherited
        usedInheritedArtifactsCoordinates.add(artifactGroupArtifactIds);
      }
    }

    // TODO Fix: The used transitive dependencies induced by inherited dependencies should be considered
    //  as used inherited
    for (ResolvedArtifact artifact : usedTransitiveArtifacts) {
      String artifactGroupArtifactIds = getName(artifact, ignoreConfiguration);
      if (artifactGroupArtifactIds == null) continue;
      usedTransitiveArtifactsCoordinates.add(artifactGroupArtifactIds);
    }

    // --- unused dependencies
    Set<String> unusedDirectArtifactsCoordinates = new HashSet<>();
    Set<String> unusedInheritedArtifactsCoordinates = new HashSet<>();
    Set<String> unusedTransitiveArtifactsCoordinates = new HashSet<>();

    for (ResolvedArtifact artifact : unusedDirectArtifacts) {
      String artifactGroupArtifactIds = getName(artifact, ignoreConfiguration);
      if (artifactGroupArtifactIds == null) continue;
      if (declaredArtifactsGroupArtifactIds.contains(artifactGroupArtifactIds)) {
        // artifact is declared in build file
        unusedDirectArtifactsCoordinates.add(artifactGroupArtifactIds);
      } else {
        // the artifact is inherited
        unusedInheritedArtifactsCoordinates.add(artifactGroupArtifactIds);
      }
    }

    for (ResolvedArtifact artifact : unusedTransitiveArtifacts) {
      String artifactGroupArtifactIds = getName(artifact, ignoreConfiguration);
      if (artifactGroupArtifactIds == null) continue;
      unusedTransitiveArtifactsCoordinates.add(artifactGroupArtifactIds);
    }

    // Filtering with name(String) because removeAll function didn't work on Artifact.
    unusedTransitiveArtifactsCoordinates.removeAll(usedDirectArtifactsCoordinates);
    unusedTransitiveArtifactsCoordinates.removeAll(usedTransitiveArtifactsCoordinates);
    unusedTransitiveArtifactsCoordinates.removeAll(usedInheritedArtifactsCoordinates);
    unusedTransitiveArtifactsCoordinates.removeAll(unusedDirectArtifactsCoordinates);
    unusedTransitiveArtifactsCoordinates.removeAll(unusedInheritedArtifactsCoordinates);

    /* Exclude dependencies with specific scopes from the DepClean analysis */
    if (ignoreConfiguration != null) {
      usedDirectArtifactsCoordinates = excludeConfiguration(usedDirectArtifactsCoordinates, ignoreConfiguration);
      usedTransitiveArtifactsCoordinates = excludeConfiguration(usedTransitiveArtifactsCoordinates, ignoreConfiguration);
      usedInheritedArtifactsCoordinates = excludeConfiguration(usedInheritedArtifactsCoordinates, ignoreConfiguration);
      unusedDirectArtifactsCoordinates = excludeConfiguration(unusedDirectArtifactsCoordinates, ignoreConfiguration);
      unusedTransitiveArtifactsCoordinates = excludeConfiguration(unusedTransitiveArtifactsCoordinates, ignoreConfiguration);
      unusedInheritedArtifactsCoordinates = excludeConfiguration(unusedInheritedArtifactsCoordinates, ignoreConfiguration);
    }

    if (ignoreDependencies != null) {
      usedDirectArtifactsCoordinates = excludeDependencies(usedDirectArtifactsCoordinates, ignoreDependencies);
      usedTransitiveArtifactsCoordinates = excludeDependencies(usedTransitiveArtifactsCoordinates, ignoreDependencies);
      usedInheritedArtifactsCoordinates = excludeDependencies(usedInheritedArtifactsCoordinates, ignoreDependencies);
      unusedDirectArtifactsCoordinates = excludeDependencies(unusedDirectArtifactsCoordinates, ignoreDependencies);
      unusedTransitiveArtifactsCoordinates = excludeDependencies(unusedTransitiveArtifactsCoordinates, ignoreDependencies);
      unusedInheritedArtifactsCoordinates = excludeDependencies(unusedInheritedArtifactsCoordinates, ignoreDependencies);
    }

    /* Printing the results to the terminal */
    printString(SEPARATOR);
    printString(" D E P C L E A N   A N A L Y S I S   R E S U L T S");
    printString(SEPARATOR);
    printString(SEPARATOR);
    printInfoOfDependencies("Used direct dependencies", sizeOfDependencies,
            usedDirectArtifactsCoordinates);
    printInfoOfDependencies("Used inherited dependencies", sizeOfDependencies,
            usedInheritedArtifactsCoordinates);
    printInfoOfDependencies("Used transitive dependencies", sizeOfDependencies,
            usedTransitiveArtifactsCoordinates);
    printInfoOfDependencies("Potentially unused direct dependencies", sizeOfDependencies,
            unusedDirectArtifactsCoordinates);
    printInfoOfDependencies("Potentially unused inherited dependencies", sizeOfDependencies,
            unusedInheritedArtifactsCoordinates);
    printInfoOfDependencies("Potentially unused transitive dependencies", sizeOfDependencies,
            unusedTransitiveArtifactsCoordinates);

    printString(SEPARATOR);
    // If there is any dependency which is unresolved during the analysis then reporting it.
    if (!allUnresolvedDependencies.isEmpty()) {
      printString(
              "\nDependencies that can't be resolved during the analysis"
                      + " [" + allUnresolvedDependencies.size() + "]" + ": ");
      allUnresolvedDependencies.forEach(s -> printString("\t" + s));
    }

    // Configurations ignored by the depclean analysis on user's wish.
    if (ignoreConfiguration != null) {
      printString(
              "\nConfigurations ignored in the analysis by the user : "
                      + " [" + ignoreConfiguration.size() + "]" + ": ");
      ignoreConfiguration.forEach(s -> printString("\t" + s));
    }

    // Dependencies ignored by depclean analysis on user's wish.
    if (ignoreDependencies != null) {
      printString(
              "\nDependencies ignored in the analysis by the user"
                      + " [" + ignoreDependencies.size() + "]" + ": ");
      ignoreDependencies.forEach(s -> printString("\t" + s));
    }

    /* Fail the build if there are unused direct dependencies */
    if (failIfUnusedDirect && !unusedDirectArtifactsCoordinates.isEmpty()) {
      throw new GradleException("Build failed due to unused direct dependencies in the dependency tree of the project.");
    }

    /* Fail the build if there are unused direct dependencies */
    if (failIfUnusedTransitive && !unusedTransitiveArtifactsCoordinates.isEmpty()) {
      throw new GradleException("Build failed due to unused transitive dependencies in the dependency tree of the project.");
    }

    /* Fail the build if there are unused direct dependencies */
    if (failIfUnusedInherited && !unusedInheritedArtifactsCoordinates.isEmpty()) {
      throw new GradleException("Build failed due to unused inherited dependencies in the dependency tree of the project.");
    }
  }

  /**
   * Get project's configuration.
   *
   * @param project Project
   * @return Project's configuration.
   */
  public static Set<Configuration> getProjectConfigurations(Project project) {
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
  public static Set<ResolvedDependency> getAllDependencies(Set<Configuration> configurations) {
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
   * If there is any dependency which remain unresolved during the analysis,then we should report them.
   *
   * @param configurations All configurations of the project.
   * @return A set of all unresolved dependencies.
   */
  public static Set<UnresolvedDependency> getAllUnresolvedDependencies(Set<Configuration> configurations) {
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
  public static Set<ResolvedDependency> getDeclaredDependencies(Set<Configuration> configurations) {
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
  public static Set<ResolvedArtifact> getDeclaredArtifacts(Set<ResolvedDependency> declaredDependency) {
    Set<ResolvedArtifact> declaredArtifacts = new HashSet<>();
    for (ResolvedDependency dependency : declaredDependency) {
      declaredArtifacts.addAll(dependency.getModuleArtifacts());
    }
    return declaredArtifacts;
  }

  /**
   * Util function to print the information of the analyzed artifacts.
   *
   * @param info               The usage status (used or unused) and type (direct, transitive, inherited) of artifacts.
   * @param sizeOfDependencies The size of the JAR file of the artifact.
   * @param dependencies       The GAV of the artifact.
   */
  private void printInfoOfDependencies(final String info, final Map<String,
          Long> sizeOfDependencies,
                                       final Set<String> dependencies) {
    printString(info.toUpperCase() + " [" + dependencies.size() + "]" + ": ");
    printDependencies(sizeOfDependencies, dependencies);
  }

  /**
   * To print a string in a new line.
   *
   * @param string String to be printed.
   */
  private void printString(final String string) {
    System.out.println(string); //NOSONAR avoid a warning of non-used logger
  }

  /**
   * Print the status of the dependencies to the standard output. The format is: "[coordinates][scope] [(size)]"
   *
   * @param sizeOfDependencies A map with the size of the dependencies.
   * @param dependencies       The set dependencies to print.
   */
  private void printDependencies(final Map<String, Long> sizeOfDependencies, final Set<String> dependencies) {
    dependencies
            .stream()
            .sorted(Comparator.comparing(o -> getSizeOfDependency(sizeOfDependencies, o)))
            .collect(Collectors.toCollection(LinkedList::new))
            .descendingIterator()
            .forEachRemaining(s -> printString("\t" + s + " (" + getSize(s, sizeOfDependencies) + ")"));
  }

  /**
   * Utility method to obtain the size of a dependency from a map of dependency -> size. If the size of the dependency
   * cannot be obtained form the map (no key with the name of the dependency exists), then it returns 0.
   *
   * @param sizeOfDependencies A map of dependency -> size.
   * @param dependency         The coordinates of a dependency.
   * @return The size of the dependency if its name is a key in the map, otherwise it returns 0.
   */
  private Long getSizeOfDependency(final Map<String, Long> sizeOfDependencies, final String dependency) {
    Long size = sizeOfDependencies.get(dependency + ".jar");
    return Objects.requireNonNullElse(size, 0L);
  }

  /**
   * Get the size of the dependency in human readable format.
   *
   * @param dependency         The dependency.
   * @param sizeOfDependencies A map with the size of the dependencies, keys are stored as the downloaded jar file i.e.,
   *                           [artifactId]-[version].jar
   * @return The human readable representation of the dependency size.
   */
  private String getSize(final String dependency, final Map<String, Long> sizeOfDependencies) {
    String[] break1 = dependency.split("\\)");
    String[] a = break1[0].split(":");
    String dep = a[1] + "-" + a[2];
    if (sizeOfDependencies.containsKey(dep + ".jar")) {
      return FileUtils.byteCountToDisplaySize(sizeOfDependencies.get(dep + ".jar"));
    } else {
      // The size cannot be obtained.
      return "size unknown";
    }
  }

  /**
   * Get names (coordinates) of the artifact. (be alert this format of getting name is very specific).
   *
   * @param artifact Artifact
   * @return Name of artifact
   */
  public String getName(ResolvedArtifact artifact, List<String> ignoreConfiguration) {
    String[] artifactGroupArtifactIds = artifact.toString().split(" \\(");
    String[] artifactGroupArtifactId = artifactGroupArtifactIds[1].split("\\)");
    if(ignoreConfiguration != null) {
      for (String configuration : ignoreConfiguration) {
        if (artifactConfigurationMap.get(artifact).equals(configuration)) return null;
      }
    }
    return artifactGroupArtifactId[0] + ":" + artifactConfigurationMap.get(artifact);
  }

  public Set<String> excludeConfiguration(Set<String> artifactCoordinates, List<String> ignoreConfiguration) {
    Set<String> nonExcludedConfigurations = new HashSet<>();
    for (String coordinates : artifactCoordinates) {
      String configuration = coordinates.split(":")[3];
      if (!ignoreConfiguration.contains(configuration)) {
        nonExcludedConfigurations.add(coordinates);
      }
    }
    return nonExcludedConfigurations;
  }

  public Set<String> excludeDependencies(Set<String> artifactCoordinates, Set<String> ignoreDependencies) {
    Set<String> nonExcludedDependencies = new HashSet<>();
    for (String coordinates : artifactCoordinates) {
      if (!ignoreDependencies.contains(coordinates)) {
        nonExcludedDependencies.add(coordinates);
        ignoreDependencies.remove(coordinates);
      }
    }
    return nonExcludedDependencies;
  }


}
