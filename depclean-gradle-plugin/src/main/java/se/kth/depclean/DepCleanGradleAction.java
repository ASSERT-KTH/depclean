package se.kth.depclean;

import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.logging.Logger;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import se.kth.depclean.analysis.DefaultGradleProjectDependencyAnalyzer;
import se.kth.depclean.analysis.GradleProjectDependencyAnalysis;
import se.kth.depclean.core.util.JarUtils;
import se.kth.depclean.utils.ClassesDirectoryFinder;
import se.kth.depclean.utils.DependencyUtils;
import se.kth.depclean.utils.GradleWritingUtils;
import se.kth.depclean.utils.json.JsonResultWriter;

/** Depclean default and only action. */
public class DepCleanGradleAction implements Action<Project> {

  // To get some clear visible results.
  private static final String SEPARATOR = "-------------------------------------------------------";

  private static final String BUILD_DIR = "build";

  /** A map [artifact] -> [configuration]. */
  private static Map<ResolvedArtifact, String> ArtifactConfigurationMap = new HashMap<>();

  private static void setArtifactConfigurationMap(Map<ResolvedArtifact, String> map) {
    ArtifactConfigurationMap = map;
  }

  /** A map [dependencies] -> [size]. */
  private static final Map<String, Long> SizeOfDependencies = new HashMap<>();

  // Extensions fields =====================================
  @Nullable private Project project = null; // Will be set in execute method
  private boolean skipDepClean;
  private boolean isIgnoreTest;
  private boolean failIfUnusedDirect;
  private boolean failIfUnusedTransitive;
  private boolean failIfUnusedInherited;
  private boolean createBuildDebloated;
  private boolean createResultJson;
  private boolean createClassUsageCsv;
  private Set<String> ignoreConfiguration = new HashSet<>();
  private Set<String> ignoreDependencies = new HashSet<>();

  @Override
  public void execute(@NonNull Project project) {

    Logger logger = project.getLogger();

    // If the user provided some configuration.
    DepCleanGradlePluginExtension extension =
        project.getExtensions().getByType(DepCleanGradlePluginExtension.class);
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
    final Path projectDirPath = Paths.get(project.getProjectDir().getAbsolutePath());

    // Path to the dependency directory.
    final Path dependencyDirPath = projectDirPath.resolve(Paths.get(BUILD_DIR, "Dependency"));

    // Path to the libs directory.
    final Path libsDirPath = projectDirPath.resolve(Paths.get(BUILD_DIR, "libs"));

    DependencyUtils utils = new DependencyUtils();

    // Project's configurations - only get resolvable ones to avoid deprecated API
    // usage
    Set<Configuration> configurations = utils.getResolvableConfigurations(project);

    // All resolved dependencies including transitive ones of the project.
    Set<ResolvedDependency> allDependencies = utils.getAllDependencies(configurations);

    // all resolved artifacts of this project
    Set<ResolvedArtifact> allArtifacts = utils.getAllArtifacts(allDependencies);

    // all unresolved dependencies including transitive ones of the project.
    Set<UnresolvedDependency> allUnresolvedDependencies =
        utils.getAllUnresolvedDependencies(configurations);

    // All declared dependencies of the project.
    Set<ResolvedDependency> declaredDependencies = utils.getDeclaredDependencies(configurations);

    setArtifactConfigurationMap(utils.getArtifactConfigurationMap());

    /*
     * Both directions of the relation between a first-level dependency and what it induces
     * transitively. Needed so that ignoring a direct dependency also drops everything that dependency
     * pulls in, and so that transitives can be attributed to inherited parents.
     */
    DependencyGraph dependencyGraph = buildDependencyGraph(declaredDependencies, allArtifacts);

    /*
     * A first-level dependency that the build does not declare itself was contributed by an applied
     * plugin or a convention, so it is reported as inherited rather than as direct.
     */
    Set<String> selfDeclaredModules =
        utils.getSelfDeclaredModules(new HashSet<>(project.getConfigurations()));
    Set<String> inheritedCoordinates = new HashSet<>();
    for (ResolvedDependency declared : declaredDependencies) {
      String module = declared.getModuleGroup() + ":" + declared.getModuleName();
      if (!selfDeclaredModules.contains(module)) {
        for (ResolvedArtifact artifact : declared.getModuleArtifacts()) {
          inheritedCoordinates.add(getName(artifact));
        }
      }
    }

    prepareDependencyDirectory(project, dependencyDirPath, libsDirPath, allArtifacts, logger);

    /* Analyze dependencies usage status */
    DefaultGradleProjectDependencyAnalyzer dependencyAnalyzer =
        new DefaultGradleProjectDependencyAnalyzer(isIgnoreTest);
    GradleProjectDependencyAnalysis projectDependencyAnalysis = dependencyAnalyzer.analyze(project);

    /*
     * Collecting the dependencies in their respective categories after the
     * dependency analysis has been completed.
     */
    assert projectDependencyAnalysis != null;
    Set<ResolvedArtifact> usedTransitiveArtifacts =
        projectDependencyAnalysis.getUsedUndeclaredArtifacts();
    Set<ResolvedArtifact> usedDirectArtifacts =
        projectDependencyAnalysis.getUsedDeclaredArtifacts();
    Set<ResolvedArtifact> unusedDirectArtifacts =
        projectDependencyAnalysis.getUnusedDeclaredArtifacts();
    Set<ResolvedArtifact> unusedTransitiveArtifacts = new HashSet<>(allArtifacts);

    CoordinateSets coordinates =
        computeCoordinates(
            usedDirectArtifacts,
            usedTransitiveArtifacts,
            unusedDirectArtifacts,
            unusedTransitiveArtifacts,
            dependencyGraph,
            inheritedCoordinates);

    /* Printing the results to the terminal */
    printAnalysisResults(coordinates, allUnresolvedDependencies);

    failBuildIfConfigured(coordinates);

    /* Writing the debloated version of the build file */
    if (createBuildDebloated) {
      writeDebloatedBuildFile(
          logger,
          projectDirPath,
          usedDirectArtifacts,
          usedTransitiveArtifacts,
          unusedTransitiveArtifacts,
          coordinates.unusedTransitive(),
          allDependencies);
    }

    /* Writing the JSON file with the debloat results */
    if (createResultJson) {
      writeJsonResult(
          logger, projectDirPath, project, dependencyAnalyzer, declaredDependencies, coordinates);
    }
  }

  /** The six coordinate categories produced by the analysis. */
  private record CoordinateSets(
      Set<String> usedDirect,
      Set<String> usedInherited,
      Set<String> usedTransitive,
      Set<String> unusedDirect,
      Set<String> unusedInherited,
      Set<String> unusedTransitive) {}

  /** Copies dependencies locally, registers their sizes and decompresses them. */
  private void prepareDependencyDirectory(
      Project project,
      Path dependencyDirPath,
      Path libsDirPath,
      Set<ResolvedArtifact> allArtifacts,
      Logger logger) {
    // Copying dependencies locally to get their size.
    File dependencyDirectory = copyDependenciesLocally(dependencyDirPath, allArtifacts, logger);

    // Copying files from libs directory to dependency directory.
    if (libsDirPath.toFile().exists()) {
      try {
        FileUtils.copyDirectory(libsDirPath.toFile(), dependencyDirPath.toFile());
      } catch (IOException | NullPointerException e) {
        logger.error("Error copying directory libs to dependency");
      }
    }

    // First, add the size of the project, as the sum of all the compiled class directories
    // (source sets and Android variants included).
    String projectJar = project.getName() + "-" + project.getVersion() + ".jar";
    long projectSize = 0;
    for (File classesDir : ClassesDirectoryFinder.findClassesDirectories(project, isIgnoreTest)) {
      projectSize += FileUtils.sizeOf(classesDir);
    }
    SizeOfDependencies.put(projectJar, projectSize);

    /*
     * Now adding the size of all the files one by one from the dependency
     * directory (build/Dependency).
     */
    addDependencySize(dependencyDirPath, logger);

    /* Decompress dependencies */
    decompressDependencies(dependencyDirectory, dependencyDirPath.toString());
  }

  /** Splits the analysed artifacts into the six coordinate categories. */
  private CoordinateSets computeCoordinates(
      Set<ResolvedArtifact> usedDirectArtifacts,
      Set<ResolvedArtifact> usedTransitiveArtifacts,
      Set<ResolvedArtifact> unusedDirectArtifacts,
      Set<ResolvedArtifact> unusedTransitiveArtifacts,
      DependencyGraph dependencyGraph,
      Set<String> inheritedCoordinates) {
    // --- used dependencies
    Set<String> usedDirectArtifactsCoordinates = new HashSet<>();
    Set<String> usedInheritedArtifactsCoordinates = new HashSet<>();
    Set<String> usedTransitiveArtifactsCoordinates = new HashSet<>();

    partitionByInherited(
        usedDirectArtifacts,
        inheritedCoordinates,
        usedDirectArtifactsCoordinates,
        usedInheritedArtifactsCoordinates);

    /*
     * A used dependency that the build does not declare is reported as inherited when every
     * first-level dependency pulling it in was itself inherited, and as transitive otherwise.
     */
    for (ResolvedArtifact artifact : usedTransitiveArtifacts) {
      String coordinates = getName(artifact);
      if (isInducedByInheritedOnly(coordinates, dependencyGraph, inheritedCoordinates)) {
        usedInheritedArtifactsCoordinates.add(coordinates);
      } else {
        usedTransitiveArtifactsCoordinates.add(coordinates);
      }
    }

    // --- unused dependencies
    Set<String> unusedDirectArtifactsCoordinates = new HashSet<>();
    Set<String> unusedInheritedArtifactsCoordinates = new HashSet<>();
    Set<String> unusedTransitiveArtifactsCoordinates = new HashSet<>();

    partitionByInherited(
        unusedDirectArtifacts,
        inheritedCoordinates,
        unusedDirectArtifactsCoordinates,
        unusedInheritedArtifactsCoordinates);

    // Same attribution as for the used dependencies above.
    for (ResolvedArtifact artifact : unusedTransitiveArtifacts) {
      String coordinates = getName(artifact);
      if (isInducedByInheritedOnly(coordinates, dependencyGraph, inheritedCoordinates)) {
        unusedInheritedArtifactsCoordinates.add(coordinates);
      } else {
        unusedTransitiveArtifactsCoordinates.add(coordinates);
      }
    }

    // Filtering with name(String) because removeAll function didn't work on
    // Artifact.
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
      unusedTransitiveArtifactsCoordinates =
          excludeConfiguration(unusedTransitiveArtifactsCoordinates);
      unusedInheritedArtifactsCoordinates =
          excludeConfiguration(unusedInheritedArtifactsCoordinates);
    }

    // Excluding dependencies ignored by the user from post analysis result.
    if (ignoreDependencies != null) {
      /*
       * Ignoring a direct dependency also ignores everything it induces: a transitive dependency
       * cannot be removed on its own, so leaving it in the analysis would report it as unused even
       * though the user explicitly asked DepClean to leave that subtree alone.
       */
      for (String ignored : new ArrayList<>(ignoreDependencies)) {
        ignoreDependencies.addAll(
            dependencyGraph.inducedBy().getOrDefault(ignored, Collections.emptySet()));
      }
      usedDirectArtifactsCoordinates = excludeDependencies(usedDirectArtifactsCoordinates);
      usedTransitiveArtifactsCoordinates = excludeDependencies(usedTransitiveArtifactsCoordinates);
      usedInheritedArtifactsCoordinates = excludeDependencies(usedInheritedArtifactsCoordinates);
      unusedDirectArtifactsCoordinates = excludeDependencies(unusedDirectArtifactsCoordinates);
      unusedTransitiveArtifactsCoordinates =
          excludeDependencies(unusedTransitiveArtifactsCoordinates);
      unusedInheritedArtifactsCoordinates =
          excludeDependencies(unusedInheritedArtifactsCoordinates);
    }

    return new CoordinateSets(
        usedDirectArtifactsCoordinates,
        usedInheritedArtifactsCoordinates,
        usedTransitiveArtifactsCoordinates,
        unusedDirectArtifactsCoordinates,
        unusedInheritedArtifactsCoordinates,
        unusedTransitiveArtifactsCoordinates);
  }

  /**
   * Adds each first-level artifact's coordinates to the declared or inherited output set. An
   * artifact is inherited when it was contributed by a plugin or a convention rather than declared
   * by the build itself.
   */
  private static void partitionByInherited(
      Set<ResolvedArtifact> artifacts,
      Set<String> inheritedCoordinates,
      Set<String> declaredOut,
      Set<String> inheritedOut) {
    for (ResolvedArtifact artifact : artifacts) {
      String artifactGroupArtifactIds = getName(artifact);
      if (inheritedCoordinates.contains(artifactGroupArtifactIds)) {
        inheritedOut.add(artifactGroupArtifactIds);
      } else {
        declaredOut.add(artifactGroupArtifactIds);
      }
    }
  }

  /**
   * Both directions of the relation between a first-level dependency and what it induces.
   *
   * @param inducedBy First-level coordinates -> the coordinates they induce.
   * @param inducedRoots Coordinates -> the first-level coordinates that pull them in.
   */
  private record DependencyGraph(
      Map<String, Set<String>> inducedBy, Map<String, Set<String>> inducedRoots) {}

  /**
   * Walks the resolved dependency graph and records, for every first-level dependency, the
   * coordinates it induces transitively, as well as the reverse relation.
   *
   * <p>The same artifact can be resolved under several configurations, so the coordinates are
   * indexed by group, artifact and version first and expanded back to full coordinates afterwards.
   *
   * @param declaredDependencies First-level dependencies declared by the project.
   * @param allArtifacts Every resolved artifact of the project.
   * @return The dependency graph in both directions.
   */
  private static DependencyGraph buildDependencyGraph(
      final Set<ResolvedDependency> declaredDependencies,
      final Set<ResolvedArtifact> allArtifacts) {

    Map<String, Set<String>> coordinatesByGroupArtifactVersion =
        indexCoordinatesByGroupArtifactVersion(allArtifacts);

    Map<String, Set<String>> inducedBy = new HashMap<>();
    Map<String, Set<String>> inducedRoots = new HashMap<>();
    for (ResolvedDependency declared : declaredDependencies) {
      Set<String> induced = collectInducedCoordinates(declared, coordinatesByGroupArtifactVersion);
      for (String root : namesOf(declared)) {
        inducedBy.put(root, induced);
        recordInducedRoots(inducedRoots, root, induced);
      }
    }
    return new DependencyGraph(inducedBy, inducedRoots);
  }

  /**
   * Indexes every resolved coordinate by its group, artifact and version part, so that a module
   * found while walking the graph can be expanded back to every coordinate it resolves under.
   */
  private static Map<String, Set<String>> indexCoordinatesByGroupArtifactVersion(
      final Set<ResolvedArtifact> allArtifacts) {
    Map<String, Set<String>> coordinatesByGroupArtifactVersion = new HashMap<>();
    for (ResolvedArtifact artifact : allArtifacts) {
      String coordinates = getName(artifact);
      coordinatesByGroupArtifactVersion
          .computeIfAbsent(groupArtifactVersion(coordinates), key -> new HashSet<>())
          .add(coordinates);
    }
    return coordinatesByGroupArtifactVersion;
  }

  /** Returns the coordinates of every artifact a first-level dependency itself resolves to. */
  private static Set<String> namesOf(final ResolvedDependency dependency) {
    return dependency.getModuleArtifacts().stream()
        .map(DepCleanGradleAction::getName)
        .collect(Collectors.toSet());
  }

  /** Walks the children of a first-level dependency and collects the coordinates they induce. */
  private static Set<String> collectInducedCoordinates(
      final ResolvedDependency declared,
      final Map<String, Set<String>> coordinatesByGroupArtifactVersion) {
    Set<String> induced = new HashSet<>();
    Deque<ResolvedDependency> pending = new ArrayDeque<>(declared.getChildren());
    Set<ResolvedDependency> visited = new HashSet<>();
    while (!pending.isEmpty()) {
      ResolvedDependency dependency = pending.poll();
      // The same module can be reached through several paths, so it has to be visited once.
      if (!visited.add(dependency)) {
        continue;
      }
      for (ResolvedArtifact artifact : dependency.getModuleArtifacts()) {
        String coordinates = getName(artifact);
        induced.addAll(
            coordinatesByGroupArtifactVersion.getOrDefault(
                groupArtifactVersion(coordinates), Collections.emptySet()));
      }
      pending.addAll(dependency.getChildren());
    }
    return induced;
  }

  /** Records, for one first-level coordinate, which coordinates it pulls in. */
  private static void recordInducedRoots(
      final Map<String, Set<String>> inducedRoots, final String root, final Set<String> induced) {
    for (String coordinate : induced) {
      inducedRoots.computeIfAbsent(coordinate, key -> new HashSet<>()).add(root);
    }
  }

  /**
   * Returns the group, artifact and version part of a {@code group:artifact:version:conf}
   * coordinate.
   */
  private static String groupArtifactVersion(final String coordinates) {
    return coordinates.substring(0, coordinates.lastIndexOf(':'));
  }

  /**
   * Tells whether a transitively induced dependency should be reported as inherited, which is the
   * case when every first-level dependency that pulls it in was itself inherited.
   */
  private static boolean isInducedByInheritedOnly(
      final String coordinates,
      final DependencyGraph graph,
      final Set<String> inheritedCoordinates) {
    Set<String> roots = graph.inducedRoots().get(coordinates);
    return roots != null && !roots.isEmpty() && inheritedCoordinates.containsAll(roots);
  }

  /** Prints the analysis results to the terminal. */
  private void printAnalysisResults(
      CoordinateSets coordinates, Set<UnresolvedDependency> allUnresolvedDependencies) {
    printString(SEPARATOR);
    printString(" D E P C L E A N   A N A L Y S I S   R E S U L T S");
    printString(SEPARATOR);
    printString(SEPARATOR);
    printInfoOfDependencies("Used direct dependencies", coordinates.usedDirect());
    printInfoOfDependencies("Used inherited dependencies", coordinates.usedInherited());
    printInfoOfDependencies("Used transitive dependencies", coordinates.usedTransitive());
    printInfoOfDependencies("Potentially unused direct dependencies", coordinates.unusedDirect());
    printInfoOfDependencies(
        "Potentially unused inherited dependencies", coordinates.unusedInherited());
    printInfoOfDependencies(
        "Potentially unused transitive dependencies", coordinates.unusedTransitive());

    printString(SEPARATOR);

    // If there is any dependency which is unresolved during the analysis then
    // reporting it.
    if (!allUnresolvedDependencies.isEmpty()) {
      printString(
          "\nDependencies that can't be resolved during the analysis"
              + " ["
              + allUnresolvedDependencies.size()
              + "]"
              + ": ");
      allUnresolvedDependencies.forEach(s -> printString("\t" + s));
    }

    // Configurations ignored by the depclean analysis on user's wish.
    if (ignoreConfiguration != null && !ignoreConfiguration.isEmpty()) {
      printString(
          "\nConfigurations ignored in the analysis by the user : "
              + " ["
              + ignoreConfiguration.size()
              + "]"
              + ": ");
      ignoreConfiguration.forEach(s -> printString("\t" + s));
    }

    // Dependencies ignored by depclean analysis on user's wish.
    if (ignoreDependencies != null && !ignoreDependencies.isEmpty()) {
      printString(
          "\nDependencies ignored in the analysis by the user"
              + " ["
              + ignoreDependencies.size()
              + "]"
              + ": ");
      ignoreDependencies.forEach(s -> printString("\t" + s));
    }
  }

  /** Fails the build if configured to do so and unused dependencies were found. */
  private void failBuildIfConfigured(CoordinateSets coordinates) {
    /* Fail the build if there are unused direct dependencies */
    if (failIfUnusedDirect && !coordinates.unusedDirect().isEmpty()) {
      throw new GradleException(
          "Build failed due to unused direct dependencies"
              + " in the dependency tree of the project.");
    }

    /* Fail the build if there are unused transitive dependencies */
    if (failIfUnusedTransitive && !coordinates.unusedTransitive().isEmpty()) {
      throw new GradleException(
          "Build failed due to unused transitive dependencies"
              + " in the dependency tree of the project.");
    }

    /* Fail the build if there are unused inherited dependencies */
    if (failIfUnusedInherited && !coordinates.unusedInherited().isEmpty()) {
      throw new GradleException(
          "Build failed due to unused inherited dependencies"
              + " in the dependency tree of the project.");
    }
  }

  /**
   * A multi-map [parent] -> [child] i.e. this will keep a track of from which dependency the unused
   * transitive dependencies should be excluded. Also, here multi-map is preferred as one transitive
   * dependency can have more than one parent.
   */
  private Multimap<String, String> computeExcludedTransitiveArtifactsMap(
      Set<ResolvedDependency> allDependencies, Set<String> unusedTransitiveArtifactsCoordinates) {
    Multimap<String, String> excludedTransitiveArtifactsMap = ArrayListMultimap.create();
    // A set that contains all the transitive children of project's dependencies.
    Set<ResolvedDependency> allChildren = getAllChildren(allDependencies);
    for (String artifact : unusedTransitiveArtifactsCoordinates) {
      String unusedTransitiveDependencyId = getArtifactGroupArtifactId(artifact);
      for (ResolvedDependency dependency : allChildren) {
        if (dependency.getName().equals(unusedTransitiveDependencyId)) {
          // i.e. this dependency should be excluded from all it's parents.
          Set<ResolvedDependency> parents = dependency.getParents();
          parents.forEach(
              s -> excludedTransitiveArtifactsMap.put(s.getName(), unusedTransitiveDependencyId));
          break; // Not need to check further.
        }
      }
    }
    return excludedTransitiveArtifactsMap;
  }

  /** Writes the debloated-dependencies.gradle file. */
  private void writeDebloatedBuildFile(
      Logger logger,
      Path projectDirPath,
      Set<ResolvedArtifact> usedDirectArtifacts,
      Set<ResolvedArtifact> usedTransitiveArtifacts,
      Set<ResolvedArtifact> unusedTransitiveArtifacts,
      Set<String> unusedTransitiveArtifactsCoordinates,
      Set<ResolvedDependency> allDependencies) {
    logger.lifecycle("Starting debloating dependencies");

    // All dependencies which will be added directly to the desired file.
    Set<ResolvedArtifact> dependenciesToAdd = new HashSet<>();

    /* Adding used direct dependencies */
    try {
      logger.lifecycle("Adding " + usedDirectArtifacts.size() + " used direct dependencies");
      dependenciesToAdd.addAll(usedDirectArtifacts);
    } catch (Exception e) {
      throw new GradleException("Failed to add used direct dependencies", e);
    }

    /* Add used transitive as direct dependencies */
    try {
      if (!usedTransitiveArtifacts.isEmpty()) {
        logger.lifecycle(
            "Adding "
                + usedTransitiveArtifacts.size()
                + " used transitive dependencies as direct dependencies.");
        dependenciesToAdd.addAll(usedTransitiveArtifacts);
      }
    } catch (Exception e) {
      throw new GradleException("Failed to add used transitive dependencies", e);
    }

    /* Exclude unused transitive dependencies */
    Multimap<String, String> excludedTransitiveArtifactsMap;
    try {
      if (!unusedTransitiveArtifacts.isEmpty()) {
        logger.lifecycle(
            "Excluding "
                + unusedTransitiveArtifactsCoordinates.size()
                + " unused transitive dependencies one-by-one.");
      }
      excludedTransitiveArtifactsMap =
          computeExcludedTransitiveArtifactsMap(
              allDependencies, unusedTransitiveArtifactsCoordinates);
    } catch (Exception e) {
      throw new GradleException("Failed to exclude unused transitive dependencies", e);
    }

    /* Write the debloated-dependencies.gradle file */
    final Path pathToDebloatedDependencies =
        projectDirPath.resolve("debloated-dependencies.gradle");
    File debloatedDependencies = pathToDebloatedDependencies.toFile();
    try {
      // Delete the previous existence (if exist).
      if (debloatedDependencies.exists()) {
        se.kth.depclean.util.FileUtils.forceDelete(debloatedDependencies);
        if (!debloatedDependencies.createNewFile()) {
          logger.warn("Could not recreate file " + debloatedDependencies.getAbsolutePath());
        }
      }
    } catch (IOException e) {
      logger.error("Error managing debloated dependencies file", e);
    }

    try {
      GradleWritingUtils.writeGradle(
          debloatedDependencies, dependenciesToAdd, excludedTransitiveArtifactsMap);
    } catch (IOException e) {
      throw new GradleException("Failed to write debloated-dependencies.gradle", e);
    }
    logger.lifecycle("Dependencies debloated successfully");
    logger.lifecycle(
        "debloated-dependencies.gradle file created in: " + pathToDebloatedDependencies);
  }

  /** Writes the depclean-results.json file (and optionally the class-usage.csv file). */
  private void writeJsonResult(
      Logger logger,
      Path projectDirPath,
      Project project,
      DefaultGradleProjectDependencyAnalyzer dependencyAnalyzer,
      Set<ResolvedDependency> declaredDependencies,
      CoordinateSets coordinates) {
    printString("Creating depclean-results.json, please wait...");
    final File jsonFile =
        projectDirPath.resolve(BUILD_DIR + File.separator + "depclean-results.json").toFile();
    final File classUsageFile =
        projectDirPath.resolve(BUILD_DIR + File.separator + "class-usage.csv").toFile();
    if (createClassUsageCsv) {
      printString("Creating class-usage.csv, please wait...");
      try {
        FileUtils.write(
            classUsageFile, "OriginClass,TargetClass,Dependency\n", StandardCharsets.UTF_8);
      } catch (IOException e) {
        logger.error("Error writing the CSV header.");
      }
    }
    JsonResultWriter jsonResultWriter =
        new JsonResultWriter(
            project,
            classUsageFile,
            dependencyAnalyzer,
            SizeOfDependencies,
            createClassUsageCsv,
            declaredDependencies,
            coordinates.usedDirect(),
            coordinates.usedInherited(),
            coordinates.usedTransitive(),
            coordinates.unusedDirect(),
            coordinates.unusedInherited(),
            coordinates.unusedTransitive());
    try (FileWriter fw = new FileWriter(jsonFile, StandardCharsets.UTF_8)) {
      jsonResultWriter.write(fw);
      fw.flush();
    } catch (IOException e) {
      logger.error("Unable to generate JSON file.");
    }
    if (jsonFile.exists()) {
      logger.lifecycle("depclean-results.json file created in: " + jsonFile.getAbsolutePath());
    }
    if (classUsageFile.exists()) {
      logger.lifecycle("class-usage.csv file created in: " + classUsageFile.getAbsolutePath());
    }
  }

  /**
   * A utility method to get the additional configuration of the plugin.
   *
   * @param extension Plugin extension class.
   */
  public void getPluginExtensions(@NonNull final DepCleanGradlePluginExtension extension) {
    Project extensionProject = extension.getProject();
    if (extensionProject != null) {
      this.project = extensionProject;
    }
    this.skipDepClean = extension.isSkipDepClean();
    this.isIgnoreTest = extension.isIgnoreTest();
    this.failIfUnusedDirect = extension.isFailIfUnusedDirect();
    this.failIfUnusedTransitive = extension.isFailIfUnusedTransitive();
    this.failIfUnusedInherited = extension.isFailIfUnusedInherited();
    this.createBuildDebloated = extension.isCreateBuildDebloated();
    this.createResultJson = extension.isCreateResultJson();
    this.createClassUsageCsv = extension.isCreateClassUsageCsv();
    Set<String> extensionIgnoreConfiguration = extension.getIgnoreConfiguration();
    if (extensionIgnoreConfiguration != null) {
      this.ignoreConfiguration = extensionIgnoreConfiguration;
    }
    Set<String> extensionIgnoreDependencies = extension.getIgnoreDependency();
    if (extensionIgnoreDependencies != null) {
      this.ignoreDependencies = extensionIgnoreDependencies;
    }
  }

  /**
   * Copies the dependency locally inside the build/Dependency directory.
   *
   * @param dependencyDirPath Directory path
   * @param allArtifacts All project's artifacts (all dependencies)
   * @param logger Logger for error reporting
   * @return A file which contain the copied dependencies.
   */
  @NonNull
  public File copyDependenciesLocally(
      @NonNull final Path dependencyDirPath,
      @NonNull final Set<ResolvedArtifact> allArtifacts,
      @NonNull final Logger logger) {
    File dependencyDirectory = dependencyDirPath.toFile();
    for (ResolvedArtifact artifact : allArtifacts) {
      // copying jar files directly from the user's .m2 directory
      File jarFile = artifact.getFile();
      if (jarFile.getAbsolutePath().endsWith(".jar")) {
        try {
          FileUtils.copyFileToDirectory(jarFile, dependencyDirectory);
        } catch (IOException e) {
          logger.error("Error copying jar file: " + jarFile.getAbsolutePath(), e);
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
  public void addDependencySize(
      @NonNull final Path dependencyDirPath, @NonNull final Logger logger) {
    if (dependencyDirPath.toFile().exists()) {
      Iterator<File> iterator =
          FileUtils.iterateFiles(dependencyDirPath.toFile(), new String[] {"jar"}, true);
      while (iterator.hasNext()) {
        File file = iterator.next();
        SizeOfDependencies.put(file.getName(), FileUtils.sizeOf(file));
      }
    } else {
      logger.warn("Dependencies were not copied locally");
    }
  }

  /**
   * Only decompress the jar files inside any directory.
   *
   * @param dependencyDirectory The directory.
   * @param dependencyDirPath Path to the directory.
   */
  public void decompressDependencies(
      @NonNull final File dependencyDirectory, @NonNull final String dependencyDirPath) {
    if (dependencyDirectory.exists()) {
      JarUtils.decompress(dependencyDirPath);
    } else {
      printString("Unable to decompress jars at " + dependencyDirPath);
    }
  }

  /**
   * Util function to print the information of the analyzed artifacts.
   *
   * @param info The usage status (used or unused) and type (direct, transitive, inherited) of
   *     artifacts.
   * @param dependencies The GAV of the artifact.
   */
  private void printInfoOfDependencies(
      @NonNull final String info, @NonNull final Set<String> dependencies) {
    printString(info.toUpperCase(Locale.ROOT) + " [" + dependencies.size() + "]" + ": ");
    printDependencies(dependencies);
  }

  /**
   * To print a string in a new line.
   *
   * @param string String to be printed.
   */
  private void printString(@NonNull final String string) {
    System.out.println(string); // NOSONAR avoid a warning of non-used logger
  }

  /**
   * Print the status of the dependencies to the standard output. The format is:
   * "[coordinates][scope] [(size)]"
   *
   * @param dependencies The set dependencies to print.
   */
  private void printDependencies(@NonNull final Set<String> dependencies) {
    List<String> sortedDependencies =
        dependencies.stream()
            .sorted(Comparator.comparing(this::getSizeOfDependency).reversed())
            .toList();
    sortedDependencies.forEach(s -> printString("\t" + s + " (" + getSize(s) + ")"));
  }

  /**
   * Utility method to obtain the size of a dependency from a map of dependency -> size. If the size
   * of the dependency cannot be obtained form the map (no key with the name of the dependency
   * exists), then it returns 0.
   *
   * @param dependency The coordinates of a dependency.
   * @return The size of the dependency if its name is a key in the map, otherwise it returns 0.
   */
  @NonNull
  private Long getSizeOfDependency(@NonNull final String dependency) {
    List<String> parts = Splitter.on(':').splitToList(dependency);
    String dep = parts.get(1) + "-" + parts.get(2);
    Long size = SizeOfDependencies.get(dep + ".jar");
    return Objects.requireNonNullElse(size, 0L);
  }

  /**
   * Get the size of the dependency in human readable format.
   *
   * @param dependency The dependency.
   * @return The human readable representation of the dependency size.
   */
  @NonNull
  private String getSize(@NonNull final String dependency) {
    List<String> break1 = Splitter.on(')').splitToList(dependency);
    List<String> a = Splitter.on(':').splitToList(break1.get(0));
    String dep = a.get(1) + "-" + a.get(2);
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
  @NonNull
  public static String getName(@NonNull final ResolvedArtifact artifact) {
    String configuration = ArtifactConfigurationMap.get(artifact);
    // Normalize configuration names for backward compatibility with test
    // expectations
    String normalizedConfiguration = normalizeConfigurationName(configuration);
    return artifact.getModuleVersion() + ":" + normalizedConfiguration;
  }

  /**
   * Normalize configuration names to maintain backward compatibility. Maps modern Gradle
   * configuration names to legacy names expected by tests.
   */
  private static String normalizeConfigurationName(@Nullable String configuration) {
    if (configuration == null) {
      return "compile"; // default fallback
    }

    // Map modern configuration names back to legacy names for display
    return switch (configuration) {
      case "runtimeElements", "runtimeClasspath", "apiElements", "compileClasspath" -> "compile";
      case "testRuntimeElements",
          "testRuntimeClasspath",
          "testApiElements",
          "testCompileClasspath" ->
          "testCompile";
      default -> configuration; // keep original for other cases
    };
  }

  /**
   * Remove those artifacts coordinates which belong to the configuration, ignored by the user.
   *
   * @param artifactCoordinates Coordinates of the artifact.
   * @return Un-ignored coordinates.
   */
  @NonNull
  public Set<String> excludeConfiguration(@NonNull final Set<String> artifactCoordinates) {
    Set<String> nonExcludedConfigurations = new HashSet<>();
    for (String coordinates : artifactCoordinates) {
      String configuration = Iterables.get(Splitter.on(':').split(coordinates), 3);
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
  @NonNull
  public Set<String> excludeDependencies(@NonNull final Set<String> artifactCoordinates) {
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
  @NonNull
  public static String getArtifactGroupArtifactId(@NonNull final String artifact) {
    List<String> parts = Splitter.on(':').splitToList(artifact);
    return parts.get(0) + ":" + parts.get(1) + ":" + parts.get(2);
  }

  /**
   * Get all the transitive children of all the project's dependencies.
   *
   * @param allDependencies Set of all dependencies
   * @return Set of all children
   */
  @NonNull
  public Set<ResolvedDependency> getAllChildren(
      @NonNull final Set<ResolvedDependency> allDependencies) {
    Set<ResolvedDependency> allChildren = new HashSet<>();
    for (ResolvedDependency dependency : allDependencies) {
      allChildren.addAll(dependency.getChildren());
    }
    return allChildren;
  }
}
