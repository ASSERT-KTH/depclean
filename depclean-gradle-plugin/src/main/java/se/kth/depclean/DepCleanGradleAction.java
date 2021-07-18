package se.kth.depclean;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.SneakyThrows;
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
  private static final String Sep = File.separator;

  /**
   * A map [artifact] -> [configuration].
   */
  private static final Map<ResolvedArtifact,
          String> ArtifactConfigurationMap = new HashMap<>();

  /**
   * A map [dependencies] -> [size].
   */
  private static final Map<String, Long> SizeOfDependencies = new HashMap<>();

  // Extensions fields =====================================
  private Project project;
  private boolean skipDepClean;
  private boolean createBuildDebloated;
  // TODO : The implementation of next two parameters will be done later.
  private boolean createResultJson;
  private boolean createClassUsageCsv;
  private boolean isIgnoreTest;
  private boolean failIfUnusedDirect;
  private boolean failIfUnusedTransitive;
  private boolean failIfUnusedInherited;
  private Set<String> ignoreConfiguration;
  private Set<String> ignoreDependencies;

  @SneakyThrows
  @Override
  public void execute(@NotNull Project project) {

    Logger logger = project.getLogger();

    // If the user provided some configuration.
    DepCleanGradlePluginExtension extension = project.getExtensions()
            .getByType(DepCleanGradlePluginExtension.class);
    getPluginExtensions(extension);

    if (skipDepClean) {
      logger.lifecycle("Skipping DepClean plugin execution");
      return;
    }

    // If the project is not the default one.
    if (this.project != null) {
      project = this.project;
    }

    // Path to the project directory.
    final String projectDirPath = project.getProjectDir().getAbsolutePath() + Sep;

    // Path to the dependency directory.
    final String dependencyDirPath = projectDirPath + "build" + Sep + "Dependency";

    // Path to the libs directory.
    final String libsDirPath = projectDirPath + "build" + Sep + "libs";

    // Path to the build classes directory.
    final String classesDirPath = projectDirPath + "build" + Sep + "classes";

    // Project's configurations.
    Set<Configuration> configurations = getProjectConfigurations(project);

    // Setting can be resolved to true to get transitive dependencies of the project's
    // configuration. Also, it is mandatory to change this parameter before runtime.
    configurations.stream().iterator().forEachRemaining(
            configuration -> configuration.setCanBeResolved(true));

    // All resolved dependencies including transitive ones of the project.
    Set<ResolvedDependency> allDependencies =
            getAllDependencies(configurations);

    // all resolved artifacts of this project
    Set<ResolvedArtifact> allArtifacts =
            getAllArtifacts(allDependencies);

    // all unresolved dependencies including transitive ones of the project.
    Set<UnresolvedDependency> allUnresolvedDependencies =
            getAllUnresolvedDependencies(configurations);

    // All declared dependencies of the project.
    Set<ResolvedDependency> declaredDependencies =
            getDeclaredDependencies(configurations);

    // All declared artifacts of the project.
    Set<ResolvedArtifact> declaredArtifacts =
            getDeclaredArtifacts(declaredDependencies);

    // Adding coordinates of the declared artifacts.
    Set<String> declaredArtifactsGroupArtifactIds = new HashSet<>();
    for (ResolvedArtifact artifact : declaredArtifacts) {
      String name = getName(artifact);
      declaredArtifactsGroupArtifactIds.add(name);
    }

    // Copying dependencies locally to get their size.
    File dependencyDirectory = copyDependenciesLocally(dependencyDirPath, allArtifacts);

    // Copying files from libs directory to dependency directory.
    if (new File(libsDirPath).exists()) {
      try {
        FileUtils.copyDirectory(new File(libsDirPath), new File(dependencyDirPath));
      } catch (IOException | NullPointerException e) {
        logger.error("Error copying directory libs to dependency");
      }
    }

    // First, add the size of the project, as the sum of all the files in target/classes
    String projectJar = project.getName() + "-" + project.getVersion() + ".jar";
    long projectSize = FileUtils.sizeOf(new File(classesDirPath));
    SizeOfDependencies.put(projectJar, projectSize);

    /* Now adding the size of all the files one by one from the dependency
       directory (build/Dependency). */
    addDependencySize(dependencyDirPath, logger);

    /* Decompress dependencies */
    decompressDependencies(dependencyDirectory, dependencyDirPath);

    /* Analyze dependencies usage status */
    GradleProjectDependencyAnalysis projectDependencyAnalysis = null;
    DefaultGradleProjectDependencyAnalyzer dependencyAnalyzer =
            new DefaultGradleProjectDependencyAnalyzer(isIgnoreTest);
    try {
      projectDependencyAnalysis = dependencyAnalyzer.analyze(project);
    } catch (ProjectDependencyAnalyzerException e) {
      logger.error("Unable to analyze dependencies.");
    }

    /* Collecting the dependencies in their respective categories after the
       dependency analysis has been completed. */
    assert projectDependencyAnalysis != null;
    Set<ResolvedArtifact> usedTransitiveArtifacts =
            projectDependencyAnalysis.getUsedUndeclaredArtifacts();
    Set<ResolvedArtifact> usedDirectArtifacts =
            projectDependencyAnalysis.getUsedDeclaredArtifacts();
    Set<ResolvedArtifact> unusedDirectArtifacts =
            projectDependencyAnalysis.getUnusedDeclaredArtifacts();
    Set<ResolvedArtifact> unusedTransitiveArtifacts = new HashSet<>(allArtifacts);

    // --- used dependencies
    Set<String> usedDirectArtifactsCoordinates = new HashSet<>();
    Set<String> usedInheritedArtifactsCoordinates = new HashSet<>();
    Set<String> usedTransitiveArtifactsCoordinates = new HashSet<>();

    for (ResolvedArtifact artifact : usedDirectArtifacts) {
      String artifactGroupArtifactIds = getName(artifact);
      if (declaredArtifactsGroupArtifactIds.contains(artifactGroupArtifactIds)) {
        // the artifact is declared in the build file
        usedDirectArtifactsCoordinates.add(artifactGroupArtifactIds);
      } else {
        // the artifact is inherited
        usedInheritedArtifactsCoordinates.add(artifactGroupArtifactIds);
      }
    }

    // TODO Fix: The used transitive dependencies induced by inherited
    //  dependencies should be considered as used inherited.
    for (ResolvedArtifact artifact : usedTransitiveArtifacts) {
      String artifactGroupArtifactIds = getName(artifact);
      usedTransitiveArtifactsCoordinates.add(artifactGroupArtifactIds);
    }

    // --- unused dependencies
    Set<String> unusedDirectArtifactsCoordinates = new HashSet<>();
    Set<String> unusedInheritedArtifactsCoordinates = new HashSet<>();
    Set<String> unusedTransitiveArtifactsCoordinates = new HashSet<>();

    for (ResolvedArtifact artifact : unusedDirectArtifacts) {
      String artifactGroupArtifactIds = getName(artifact);
      if (declaredArtifactsGroupArtifactIds.contains(artifactGroupArtifactIds)) {
        // artifact is declared in build file
        unusedDirectArtifactsCoordinates.add(artifactGroupArtifactIds);
      } else {
        // the artifact is inherited
        unusedInheritedArtifactsCoordinates.add(artifactGroupArtifactIds);
      }
    }

    for (ResolvedArtifact artifact : unusedTransitiveArtifacts) {
      String artifactGroupArtifactIds = getName(artifact);
      unusedTransitiveArtifactsCoordinates.add(artifactGroupArtifactIds);
    }

    // Filtering with name(String) because removeAll function didn't work on Artifact.
    unusedTransitiveArtifactsCoordinates.removeAll(usedDirectArtifactsCoordinates);
    unusedTransitiveArtifactsCoordinates.removeAll(usedTransitiveArtifactsCoordinates);
    unusedTransitiveArtifactsCoordinates.removeAll(usedInheritedArtifactsCoordinates);
    unusedTransitiveArtifactsCoordinates.removeAll(unusedDirectArtifactsCoordinates);
    unusedTransitiveArtifactsCoordinates.removeAll(unusedInheritedArtifactsCoordinates);

    // Exclude dependencies with specific scopes from the post analysis result.
    if (ignoreConfiguration != null) {
      usedDirectArtifactsCoordinates = excludeConfiguration(usedDirectArtifactsCoordinates);
      usedTransitiveArtifactsCoordinates = excludeConfiguration(usedTransitiveArtifactsCoordinates);
      usedInheritedArtifactsCoordinates = excludeConfiguration(usedInheritedArtifactsCoordinates);
      unusedDirectArtifactsCoordinates = excludeConfiguration(unusedDirectArtifactsCoordinates);
      unusedTransitiveArtifactsCoordinates = excludeConfiguration(unusedTransitiveArtifactsCoordinates);
      unusedInheritedArtifactsCoordinates = excludeConfiguration(unusedInheritedArtifactsCoordinates);
    }

    // Excluding dependencies ignored by the user from post analysis result.
    // TODO : If a direct dependency is ignored by the user then it' corresponding
    //  transitive and inherited dependencies should also be ignore.
    if (ignoreDependencies != null) {
      usedDirectArtifactsCoordinates = excludeDependencies(usedDirectArtifactsCoordinates);
      usedTransitiveArtifactsCoordinates = excludeDependencies(usedTransitiveArtifactsCoordinates);
      usedInheritedArtifactsCoordinates = excludeDependencies(usedInheritedArtifactsCoordinates);
      unusedDirectArtifactsCoordinates = excludeDependencies(unusedDirectArtifactsCoordinates);
      unusedTransitiveArtifactsCoordinates = excludeDependencies(unusedTransitiveArtifactsCoordinates);
      unusedInheritedArtifactsCoordinates = excludeDependencies(unusedInheritedArtifactsCoordinates);
    }

    /* Printing the results to the terminal */
    printString(SEPARATOR);
    printString(" D E P C L E A N   A N A L Y S I S   R E S U L T S");
    printString(SEPARATOR);
    printString(SEPARATOR);
    printInfoOfDependencies("Used direct dependencies",
            usedDirectArtifactsCoordinates);
    printInfoOfDependencies("Used inherited dependencies",
            usedInheritedArtifactsCoordinates);
    printInfoOfDependencies("Used transitive dependencies",
            usedTransitiveArtifactsCoordinates);
    printInfoOfDependencies("Potentially unused direct dependencies",
            unusedDirectArtifactsCoordinates);
    printInfoOfDependencies("Potentially unused inherited dependencies",
            unusedInheritedArtifactsCoordinates);
    printInfoOfDependencies("Potentially unused transitive dependencies",
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
      throw new GradleException("Build failed due to unused direct dependencies"
              + " in the dependency tree of the project.");
    }

    /* Fail the build if there are unused direct dependencies */
    if (failIfUnusedTransitive && !unusedTransitiveArtifactsCoordinates.isEmpty()) {
      throw new GradleException("Build failed due to unused transitive dependencies"
              + " in the dependency tree of the project.");
    }

    /* Fail the build if there are unused direct dependencies */
    if (failIfUnusedInherited && !unusedInheritedArtifactsCoordinates.isEmpty()) {
      throw new GradleException("Build failed due to unused inherited dependencies"
              + " in the dependency tree of the project.");
    }

    /* Writing the debloated version of the pom */
    if (createBuildDebloated) {
      logger.lifecycle("Starting debloating dependencies");

      // All dependencies which will be added directly to the desired file.
      Set<ResolvedArtifact> dependenciesToAdd = new HashSet<>();

      /* Adding used direct dependencies */
      try {
        logger
                .lifecycle("Adding " + usedDirectArtifacts.size()
                        + " used direct dependencies");
        dependenciesToAdd.addAll(usedDirectArtifacts);
      } catch (Exception e) {
        throw new GradleException(e.getMessage(), e);
      }

      /* Add used transitive as direct dependencies */
      try {
        if (!usedTransitiveArtifacts.isEmpty()) {
          logger
                  .lifecycle("Adding " + usedTransitiveArtifacts.size()
                          + " used transitive dependencies as direct dependencies.");
          dependenciesToAdd.addAll(usedTransitiveArtifacts);
        }
      } catch (Exception e) {
        throw new GradleException(e.getMessage(), e);
      }

      /* Exclude unused transitive dependencies */

      /* A multi-map [parent] -> [child] i.e. this will keep a track of from which dependency
         the unused transitive dependencies should be excluded. Also, here multi-map is preferred
         as one transitive dependency can have more than one parent. */
      Multimap<String, String> excludedTransitiveArtifactsMap = ArrayListMultimap.create();

      // A set that contains all the transitive children of project's dependencies.
      Set<ResolvedDependency> allChildren = getAllChildren(allDependencies);
      try {
        if (!unusedTransitiveArtifacts.isEmpty()) {
          logger
                  .lifecycle("Excluding " + unusedTransitiveArtifactsCoordinates.size()
                          + " unused transitive dependencies one-by-one.");
          for (String artifact : unusedTransitiveArtifactsCoordinates) {
            String unusedTransitiveDependencyId = getArtifactGroupArtifactId(artifact);
            for (ResolvedDependency dependency : allChildren) {
              if (dependency.getName().equals(unusedTransitiveDependencyId)) {
                // i.e. this dependency should be excluded from all it's parents.
                Set<ResolvedDependency> parents = dependency.getParents();
                parents.forEach(s -> excludedTransitiveArtifactsMap
                        .put(s.getName(), unusedTransitiveDependencyId));
                break; // Not need to check further.
              }
            }
          }
        }
      } catch (Exception e) {
        throw new GradleException(e.getMessage(), e);
      }

      /* Write the debloated-dependencies.gradle file */
      String pathToDebloatedDependencies = projectDirPath + "debloated-dependencies.gradle";
      File debloatedDependencies = new File(pathToDebloatedDependencies);
      try {
        // Delete the previous existence (if exist).
        if (debloatedDependencies.exists()) {
          se.kth.depclean.util.FileUtils.forceDelete(debloatedDependencies);
          debloatedDependencies.createNewFile();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }

      try {
        writeGradle(debloatedDependencies, dependenciesToAdd, excludedTransitiveArtifactsMap);
      } catch (IOException e) {
        throw new GradleException(e.getMessage(), e);
      }
      logger.lifecycle("Dependencies debloated successfully");
      logger.lifecycle("debloated-dependencies.gradle file created in: "
              + pathToDebloatedDependencies);
    }
  }

  /**
   * A utility method to get the additional configuration of the plugin.
   *
   * @param extension Plugin extension class.
   */
  public void getPluginExtensions(final DepCleanGradlePluginExtension extension) {
    this.project = extension.getProject();
    this.skipDepClean = extension.isSkipDepClean();
    this.createBuildDebloated = extension.isCreateBuildDebloated();
    this.createResultJson = extension.isCreateResultJson();
    this.createClassUsageCsv = extension.isCreateClassUsageCsv();
    this.isIgnoreTest = extension.isIgnoreTest();
    this.failIfUnusedDirect = extension.isFailIfUnusedDirect();
    this.failIfUnusedTransitive = extension.isFailIfUnusedTransitive();
    this.failIfUnusedInherited = extension.isFailIfUnusedInherited();
    this.ignoreConfiguration = extension.getIgnoreConfiguration();
    this.ignoreDependencies = extension.getIgnoreDependency();
  }

  /**
   * Get project's configuration.
   *
   * @param project Project
   * @return Project's configuration.
   */
  public static Set<Configuration> getProjectConfigurations(final Project project) {
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
  public static Set<ResolvedDependency> getAllDependencies(
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
  public static Set<UnresolvedDependency> getAllUnresolvedDependencies(
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
  public static Set<ResolvedDependency> getDeclaredDependencies(
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
  public static Set<ResolvedArtifact> getDeclaredArtifacts(
          final Set<ResolvedDependency> declaredDependency) {
    Set<ResolvedArtifact> declaredArtifacts = new HashSet<>();
    for (ResolvedDependency dependency : declaredDependency) {
      declaredArtifacts.addAll(dependency.getModuleArtifacts());
    }
    return declaredArtifacts;
  }

  /**
   * Copies the dependency locally inside the build/Dependency directory.
   *
   * @param dependencyDirPath Directory path
   * @param allArtifacts All project's artifacts (all dependencies)
   * @return A file which contain the copied dependencies.
   */
  public File copyDependenciesLocally(
          final String dependencyDirPath,
          final Set<ResolvedArtifact> allArtifacts) {
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
    return dependencyDirectory;
  }

  /**
   * To get the size of each dependency (artifact).
   *
   * @param dependencyDirPath Directory path where all the copied dependencies are stored.
   * @param logger To show some warnings.
   */
  public void addDependencySize(final String dependencyDirPath, final Logger logger) {
    if (Files.exists(Path.of(String.valueOf(Paths.get(
            dependencyDirPath))))) {
      Iterator<File> iterator = FileUtils.iterateFiles(
              new File(
                      dependencyDirPath), new String[]{"jar"}, true);
      while (iterator.hasNext()) {
        File file = iterator.next();
        SizeOfDependencies.put(file.getName(), FileUtils.sizeOf(file));
      }
    } else {
      logger.warn("Dependencies where not copied locally");
    }
  }

  /**
   * Only decompress the jar files inside any directory.
   *
   * @param dependencyDirectory The directory.
   * @param dependencyDirPath Path to the directory.
   */
  public void decompressDependencies(
          final File dependencyDirectory,
          final String dependencyDirPath) {
    if (dependencyDirectory.exists()) {
      JarUtils.decompressJars(dependencyDirPath);
    } else {
      printString("Unable to decompress jars at " + dependencyDirPath);
    }
  }

  /**
   * Util function to print the information of the analyzed artifacts.
   *
   * @param info The usage status (used or unused) and type (direct,
   *            transitive, inherited) of artifacts.
   * @param dependencies The GAV of the artifact.
   */
  private void printInfoOfDependencies(
          final String info,
          final Set<String> dependencies) {
    printString(info.toUpperCase() + " [" + dependencies.size() + "]" + ": ");
    printDependencies(dependencies);
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
   * Print the status of the dependencies to the standard output.
   * The format is: "[coordinates][scope] [(size)]"
   *
   * @param dependencies The set dependencies to print.
   */
  private void printDependencies(final Set<String> dependencies) {
    dependencies
            .stream()
            .sorted(Comparator.comparing(this::getSizeOfDependency))
            .collect(Collectors.toCollection(LinkedList::new))
            .descendingIterator()
            .forEachRemaining(s -> printString("\t" + s + " (" + getSize(s) + ")"));
  }

  /**
   * Utility method to obtain the size of a dependency from a map of
   * dependency -> size. If the size of the dependency cannot be obtained form
   * the map (no key with the name of the dependency exists), then it returns 0.
   *
   * @param dependency The coordinates of a dependency.
   * @return The size of the dependency if its name is a key in the map,
   *        otherwise it returns 0.
   */
  private Long getSizeOfDependency(final String dependency) {
    Long size = SizeOfDependencies.get(dependency + ".jar");
    return Objects.requireNonNullElse(size, 0L);
  }

  /**
   * Get the size of the dependency in human readable format.
   *
   * @param dependency The dependency.
   * @return The human readable representation of the dependency size.
   */
  private String getSize(final String dependency) {
    String[] break1 = dependency.split("\\)");
    String[] a = break1[0].split(":");
    String dep = a[1] + "-" + a[2];
    if (SizeOfDependencies.containsKey(dep + ".jar")) {
      return FileUtils.byteCountToDisplaySize(SizeOfDependencies.get(dep + ".jar"));
    } else {
      // The size cannot be obtained.
      return "size unknown";
    }
  }

  /**
   * Get names (coordinates) of the artifact.<br>
   * <b>NOTE</b> Be alert, this format of getting name is very specific.
   *
   * @param artifact Artifact
   * @return Name of artifact
   */
  public String getName(final ResolvedArtifact artifact) {
    String[] artifactGroupArtifactIds = artifact.toString().split(" \\(");
    String[] artifactGroupArtifactId = artifactGroupArtifactIds[1].split("\\)");
    return artifactGroupArtifactId[0] + ":" + ArtifactConfigurationMap.get(artifact);
  }

  /**
   * Remove those artifacts coordinates which belong to the configuration, ignored by the user.
   *
   * @param artifactCoordinates Coordinates of the artifact.
   * @return Un-ignored coordinates.
   */
  public Set<String> excludeConfiguration(final Set<String> artifactCoordinates) {
    Set<String> nonExcludedConfigurations = new HashSet<>();
    for (String coordinates : artifactCoordinates) {
      String configuration = coordinates.split(":")[3];
      if (!ignoreConfiguration.contains(configuration)) {
        nonExcludedConfigurations.add(coordinates);
      }
    }
    return nonExcludedConfigurations;
  }

  /**
   * Remove those artifact coordinates which are ignores by the user.
   *
   * @param artifactCoordinates Coordinates of the artifact.
   * @return Un-ignored coordinates.
   */
  public Set<String> excludeDependencies(final Set<String> artifactCoordinates) {
    Set<String> nonExcludedDependencies = new HashSet<>();
    for (String coordinates : artifactCoordinates) {
      if (!ignoreDependencies.contains(coordinates)) {
        nonExcludedDependencies.add(coordinates);
        ignoreDependencies.remove(coordinates);
      }
    }
    return nonExcludedDependencies;
  }

  /**
   * Get coordinates(name) without scopes or configuration.
   *
   * @param artifact Artifact
   * @return Name of artifact without scope.
   */
  public String getArtifactGroupArtifactId(final String artifact) {
    String[] parts = artifact.split(":");
    return parts[0] + ":" + parts[1] + ":" + parts[2];
  }

  /**
   * Get all the transitive children of all the project's dependencies.
   *
   * @param allDependencies Set of all dependencies
   * @return Set of all children
   */
  public Set<ResolvedDependency> getAllChildren(final Set<ResolvedDependency> allDependencies) {
    Set<ResolvedDependency> allChildren = new HashSet<>();
    for (ResolvedDependency dependency : allDependencies) {
      allChildren.addAll(dependency.getChildren());
    }
    return allChildren;
  }

  /**
   * Writes the debloated-dependencies.gradle.
   *
   * @param file Target
   * @param dependenciesToAdd Direct dependencies to be written directly.
   * @param excludedTransitiveArtifactsMap Map [dependency] -> [excluded transitive child]
   * @throws IOException In case of IO issues.
   */
  public void writeGradle(final File file, final Set<ResolvedArtifact> dependenciesToAdd,
                       final Multimap<String, String> excludedTransitiveArtifactsMap)
          throws IOException {
    /* A multi-map [configuration] -> [dependency] */
    Multimap<String, String> configurationDependencyMap = getNewConfigurations(dependenciesToAdd);

    /* Writing starts */
    FileWriter fileWriter = new FileWriter(file, true);
    BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
    PrintWriter writer = new PrintWriter(bufferedWriter);

    writer.println("dependencies {");

    for (String configuration : configurationDependencyMap.keySet()) {
      writer.print("\t" + configuration);

      /* Getting all the dependencies with specified configuration and converting
         it to an array for ease in writing. */
      Collection<String> dependency = configurationDependencyMap.get(configuration);
      String[] dep = dependency.toArray(new String[dependency.size()]);

      /* Writing those dependencies which do not have to exclude any dependency(s).
         Simultaneously, also getting those dependencies which have to exclude
         some transitive dependencies. */
      Set<String> excludeChildrenDependencies =
              writeNonExcluded(writer, dep, excludedTransitiveArtifactsMap);

      /* Writing those dependencies which have to exclude any dependency(s). */
      if (!excludeChildrenDependencies.isEmpty()) {
        writeExcluded(writer, configuration,
                excludeChildrenDependencies, excludedTransitiveArtifactsMap);
      }
    }
    writer.println("}");
    writer.close();
  }

  // TODO: To modify later.
  /**
   * There are some dependencies configurations that are removed by Gradle above 7.0.0,
   * like runtime converted to implementation. To know more visit <a href =
   * "https://docs.gradle.org/current/userguide/upgrading_version_6.html">here.</a>, but still
   * $dependencies.getConfiguration() still returns those deprecated scopes. <br>
   * So, currently we just divide dependencies into two parts i.e. implementation
   * & testImplementation.
   *
   * @param dependenciesToAdd All dependencies to be added.
   * @return A multi-map with value as a dependency and key as it's configuration.
   */
  public Multimap<String, String> getNewConfigurations(
          final Set<ResolvedArtifact> dependenciesToAdd) {
    Multimap<String, String> configurationDependencyMap = ArrayListMultimap.create();
    for (ResolvedArtifact artifact : dependenciesToAdd) {
      String dependency = getArtifactGroupArtifactId(getName(artifact));
      String oldConfiguration = getName(artifact).split(":")[3];
      String configuration =
              oldConfiguration.startsWith("test") || oldConfiguration.endsWith("Elements")
                      ? "testImplementation" : "implementation";
      configurationDependencyMap.put(configuration, dependency);
    }
    return configurationDependencyMap;
  }

  /**
   * Writes those dependencies which don't have to exclude any transitive dependencies of their own.
   * Simultaneously, it also returns the set of dependencies which have to exclude some
   * transitive dependencies to write them separately.
   *
   * @param writer For writing.
   * @param dep Dependencies to be printed.
   * @param excludedTransitiveArtifactsMap [dependency] -> [excluded transitive dependencies].
   * @return A set of dependencies.
   */
  public Set<String> writeNonExcluded(final PrintWriter writer,
                                      final String[] dep,
                                      final Multimap<String, String> excludedTransitiveArtifactsMap) {
    Set<String> excludeChildrenDependencies = new HashSet<>();
    int size = dep.length - 1;
    for (int i = 0; i < size; i++) {
      if (excludedTransitiveArtifactsMap.containsKey(dep[i])) {
        excludeChildrenDependencies.add(dep[i]);
      } else {
        writer.println("\t\t\t'" + dep[i] + "',");
      }
    }
    writer.println("\t\t\t'" + dep[size] + "'\n");
    return excludeChildrenDependencies;
  }

  /**
   * Writes those dependencies which have to exclude some of their transitive dependency(s).
   *
   * @param writer For writing.
   * @param configuration Corresponding configuration.
   * @param excludeChildrenDependencies Transitive dependencies to be excluded.
   * @param excludedTransitiveArtifactsMap [dependency] -> [excluded transitive dependencies].
   */
  public void writeExcluded(final PrintWriter writer,
                            final String configuration,
                            final Set<String> excludeChildrenDependencies,
                            final Multimap<String, String> excludedTransitiveArtifactsMap) {
    for (String excludeDep : excludeChildrenDependencies) {
      writer.println("\t" + configuration + " ('" + excludeDep + "') {");
      Collection<String> excludeDependencies = excludedTransitiveArtifactsMap.get(excludeDep);
      excludeDependencies.forEach(s ->
              writer.println("\t\t\texclude group: '" + s.split(":")[0]
                      + "', module: '" + s.split(":")[1] + "'"));
      writer.println("\t}");
    }
  }

}
