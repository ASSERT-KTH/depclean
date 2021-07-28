package se.kth.depclean;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.logging.Logger;
import org.jetbrains.annotations.NotNull;
import se.kth.depclean.utils.DependencyUtils;
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

  /**
   * A map [artifact] -> [configuration].
   */
  private static Map<ResolvedArtifact,
            String> ArtifactConfigurationMap = new HashMap<>();

  /**
   * A map [dependencies] -> [size].
   */
  private static final Map<String, Long> SizeOfDependencies = new HashMap<>();

  // Extensions fields =====================================
  private Project project;
  private boolean skipDepClean;
  private boolean isIgnoreTest;
  private boolean failIfUnusedDirect;

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
    final Path projectDirPath = Paths.get(project.getProjectDir().getAbsolutePath());

    // Path to the dependency directory.
    final Path dependencyDirPath = projectDirPath.resolve(Paths.get("build", "Dependency"));

    // Path to the libs directory.
    final Path libsDirPath = projectDirPath.resolve(Paths.get("build", "libs"));

    // Path to the build classes directory.
    final Path classesDirPath = projectDirPath.resolve(Paths.get("build", "classes"));

    DependencyUtils utils = new DependencyUtils();

    // Project's configurations.
    Set<Configuration> configurations = utils.getProjectConfigurations(project);

    /* Setting can be resolved to true to get transitive dependencies of the project's
       configuration. Also, it is mandatory to change this parameter before runtime. */
    configurations.stream().iterator().forEachRemaining(
            configuration -> configuration.setCanBeResolved(true));

    // All resolved dependencies including transitive ones of the project.
    Set<ResolvedDependency> allDependencies =
            utils.getAllDependencies(configurations);

    // all resolved artifacts of this project
    Set<ResolvedArtifact> allArtifacts =
            utils.getAllArtifacts(allDependencies);

    // all unresolved dependencies including transitive ones of the project.
    Set<UnresolvedDependency> allUnresolvedDependencies =
            utils.getAllUnresolvedDependencies(configurations);

    // All declared dependencies of the project.
    Set<ResolvedDependency> declaredDependencies =
            utils.getDeclaredDependencies(configurations);

    // All declared artifacts of the project.
    Set<ResolvedArtifact> declaredArtifacts =
            utils.getDeclaredArtifacts(declaredDependencies);

    ArtifactConfigurationMap = utils.getArtifactConfigurationMap();

    // Adding coordinates of the declared artifacts.
    Set<String> declaredArtifactsGroupArtifactIds = new HashSet<>();
    for (ResolvedArtifact artifact : declaredArtifacts) {
      String name = getName(artifact);
      declaredArtifactsGroupArtifactIds.add(name);
    }

    // Copying dependencies locally to get their size.
    File dependencyDirectory = copyDependenciesLocally(dependencyDirPath, allArtifacts);

    // Copying files from libs directory to dependency directory.
    if (libsDirPath.toFile().exists()) {
      try {
        FileUtils.copyDirectory(libsDirPath.toFile(), dependencyDirPath.toFile());
      } catch (IOException | NullPointerException e) {
        logger.error("Error copying directory libs to dependency");
      }
    }

    // First, add the size of the project, as the sum of all the files in target/classes
    String projectJar = project.getName() + "-" + project.getVersion() + ".jar";
    long projectSize = FileUtils.sizeOf(classesDirPath.toFile());
    SizeOfDependencies.put(projectJar, projectSize);

    /* Now adding the size of all the files one by one from the dependency
       directory (build/Dependency). */
    addDependencySize(dependencyDirPath, logger);

    /* Decompress dependencies */
    decompressDependencies(dependencyDirectory, dependencyDirPath.toString());

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

    /* Fail the build if there are unused direct dependencies */
    if (failIfUnusedDirect && !unusedDirectArtifactsCoordinates.isEmpty()) {
      throw new GradleException("Build failed due to unused direct dependencies"
              + " in the dependency tree of the project.");
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
    this.isIgnoreTest = extension.isIgnoreTest();
    this.failIfUnusedDirect = extension.isFailIfUnusedDirect();
  }

  /**
   * Copies the dependency locally inside the build/Dependency directory.
   *
   * @param dependencyDirPath Directory path
   * @param allArtifacts All project's artifacts (all dependencies)
   * @return A file which contain the copied dependencies.
   */
  public File copyDependenciesLocally(
          final Path dependencyDirPath,
          final Set<ResolvedArtifact> allArtifacts) {
    File dependencyDirectory = dependencyDirPath.toFile();
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
  public void addDependencySize(final Path dependencyDirPath, final Logger logger) {
    if (dependencyDirPath.toFile().exists()) {
      Iterator<File> iterator = FileUtils.iterateFiles(
                      dependencyDirPath.toFile(), new String[]{"jar"}, true);
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
  public static String getName(final ResolvedArtifact artifact) {
    String[] artifactGroupArtifactIds = artifact.toString().split(" \\(");
    String[] artifactGroupArtifactId = artifactGroupArtifactIds[1].split("\\)");
    return artifactGroupArtifactId[0] + ":" + ArtifactConfigurationMap.get(artifact);
  }
}
