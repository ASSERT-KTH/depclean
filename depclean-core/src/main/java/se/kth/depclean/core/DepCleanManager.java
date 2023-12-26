package se.kth.depclean.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import se.kth.depclean.core.analysis.AnalysisFailureException;
import se.kth.depclean.core.analysis.DefaultProjectDependencyAnalyzer;
import se.kth.depclean.core.analysis.model.ProjectDependencyAnalysis;
import se.kth.depclean.core.model.ClassName;
import se.kth.depclean.core.model.Dependency;
import se.kth.depclean.core.model.ProjectContext;
import se.kth.depclean.core.model.Scope;
import se.kth.depclean.core.util.JarUtils;
import se.kth.depclean.core.wrapper.DependencyManagerWrapper;
import se.kth.depclean.core.wrapper.LogWrapper;

/**
 * Runs the depclean process, regardless of a specific dependency manager.
 */
@AllArgsConstructor
@Slf4j
public class DepCleanManager {

  private static final String SEPARATOR = "-------------------------------------------------------";
  private static final String DIRECTORY_TO_EXTRACT_DEPENDENCIES = "dependency";

  private final DependencyManagerWrapper dependencyManager;
  private final boolean skipDepClean;
  private final boolean ignoreTests;
  private final Set<String> ignoreScopes;
  private final Set<String> ignoreDependencies;
  private final boolean failIfUnusedDirect;
  private final boolean failIfUnusedTransitive;
  private final boolean failIfUnusedInheritedDirect;
  private final boolean failIfUnusedInheritedTransitive;
  private final boolean createPomDebloated;
  private final boolean createResultJson;
  private final boolean createCallGraphCsv;

  /**
   * Execute the depClean manager.
   */
  @SneakyThrows
  public ProjectDependencyAnalysis execute() throws AnalysisFailureException {
    final long startTime = System.currentTimeMillis();

    if (skipDepClean) {
      getLog().info("Skipping DepClean plugin execution");
      return null;
    }
    printString(SEPARATOR);
    getLog().info("Starting DepClean dependency analysis");

    if (dependencyManager.isMaven() && dependencyManager.isPackagingPom()) {
      getLog().info("Skipping because packaging type is pom");
      return null;
    }

    extractClassesFromDependencies();

    final DefaultProjectDependencyAnalyzer projectDependencyAnalyzer = new DefaultProjectDependencyAnalyzer();
    final ProjectDependencyAnalysis analysis = projectDependencyAnalyzer.analyze(buildProjectContext());
    analysis.print();

    /* Fail the build if there are unused direct dependencies */
    if (failIfUnusedDirect && analysis.hasUnusedDirectDependencies()) {
      throw new AnalysisFailureException(
          "Build failed due to unused direct dependencies in the dependency tree of the project.");
    }

    /* Fail the build if there are unused transitive dependencies */
    if (failIfUnusedTransitive && analysis.hasUnusedTransitiveDependencies()) {
      throw new AnalysisFailureException(
          "Build failed due to unused transitive dependencies in the dependency tree of the project.");
    }

    /* Fail the build if there are unused inherited direct dependencies */
    if (failIfUnusedInheritedDirect && analysis.hasUnusedInheritedDirectDependencies()) {
      throw new AnalysisFailureException(
          "Build failed due to unused inherited direct dependencies in the dependency tree of the project.");
    }

    /* Fail the build if there are unused inherited direct dependencies */
    if (failIfUnusedInheritedTransitive && analysis.hasUnusedInheritedTransitiveDependencies()) {
      throw new AnalysisFailureException(
          "Build failed due to unused inherited transitive dependencies in the dependency tree of the project.");
    }

    /* Writing the debloated version of the pom */
    if (createPomDebloated) {
      dependencyManager.getDebloater(analysis).write();
    }

    /* Writing the JSON file with the depclean results */
    if (createResultJson) {
      createResultJson(analysis);
    }

    final long stopTime = System.currentTimeMillis();
    getLog().info("Analysis done in " + getTime(stopTime - startTime));

    return analysis;
  }

  @SneakyThrows
  private void extractClassesFromDependencies() {
    File dependencyDirectory = dependencyManager.getBuildDirectory().resolve(DIRECTORY_TO_EXTRACT_DEPENDENCIES).toFile();
    FileUtils.deleteDirectory(dependencyDirectory);
    dependencyManager.dependencyGraph().allDependencies()
        .forEach(jarFile -> copyDependencies(jarFile, dependencyDirectory));

    // Workaround for dependencies that are in located in a project's libs directory.
    if (dependencyManager.getBuildDirectory().resolve("libs").toFile().exists()) {
      try {
        FileUtils.copyDirectory(
            dependencyManager.getBuildDirectory().resolve("libs").toFile(),
            dependencyDirectory
        );
      } catch (IOException | NullPointerException e) {
        getLog().error("Error copying directory libs to" + dependencyDirectory.getAbsolutePath());
      }
    }

    /* Decompress dependencies */
    if (dependencyDirectory.exists()) {
      JarUtils.decompress(dependencyDirectory.getAbsolutePath());
    }
  }

  private void copyDependencies(Dependency dependency, File destFolder) {
    copyDependencies(dependency.getFile(), destFolder);
  }

  @SneakyThrows
  private void copyDependencies(File jarFile, File destFolder) {
    FileUtils.copyFileToDirectory(jarFile, destFolder);
  }

  private void createResultJson(ProjectDependencyAnalysis analysis) {
    printString("Creating depclean-results.json, please wait...");
    final File jsonFile = new File(dependencyManager.getBuildDirectory() + File.separator + "depclean-results.json");
    final File treeFile = new File(dependencyManager.getBuildDirectory() + File.separator + "tree.txt");
    final File csvFile = new File(dependencyManager.getBuildDirectory() + File.separator + "depclean-callgraph.csv");
    try {
      dependencyManager.generateDependencyTree(treeFile);
    } catch (IOException | InterruptedException e) {
      getLog().error("Unable to generate dependency tree.");
      // Restore interrupted state...
      Thread.currentThread().interrupt();
      return;
    }
    if (createCallGraphCsv) {
      printString("Creating " + csvFile.getName() + ", please wait...");
      try {
        FileUtils.write(csvFile, "OriginClass,TargetClass,OriginDependency,TargetDependency\n", Charset.defaultCharset());
      } catch (IOException e) {
        getLog().error("Error writing the CSV header.");
      }
    }
    String treeAsJson = dependencyManager.getTreeAsJson(
        treeFile,
        analysis,
        csvFile,
        createCallGraphCsv
    );

    try {
      FileUtils.write(jsonFile, treeAsJson, Charset.defaultCharset());
    } catch (IOException e) {
      getLog().error("Unable to generate " + jsonFile.getName() + " file.");
    }
    if (jsonFile.exists()) {
      getLog().info(jsonFile.getName() + " file created in: " + jsonFile.getAbsolutePath());
    }
    if (csvFile.exists()) {
      getLog().info(csvFile.getName() + " file created in: " + csvFile.getAbsolutePath());
    }
  }

  private ProjectContext buildProjectContext() {
    if (ignoreTests) {
      ignoreScopes.add("test");
    }

    // Consider are used all the classes declared in Maven processors
    Set<ClassName> allUsedClasses = new HashSet<>();
    Set<ClassName> usedClassesFromProcessors = dependencyManager
        .collectUsedClassesFromProcessors().stream()
        .map(ClassName::new)
        .collect(Collectors.toSet());

    // Consider as used all the classes located in the imports of the source code
    Set<ClassName> usedClassesFromSource = dependencyManager.collectUsedClassesFromSource(
            dependencyManager.getSourceDirectory(),
            dependencyManager.getTestDirectory())
        .stream()
        .map(ClassName::new)
        .collect(Collectors.toSet());

    allUsedClasses.addAll(usedClassesFromProcessors);
    allUsedClasses.addAll(usedClassesFromSource);

    return new ProjectContext(
        dependencyManager.dependencyGraph(),
        dependencyManager.getOutputDirectories(),
        dependencyManager.getTestOutputDirectories(),
        dependencyManager.getSourceDirectory(),
        dependencyManager.getTestDirectory(),
        dependencyManager.getDependenciesDirectory(),
        ignoreScopes.stream().map(Scope::new).collect(Collectors.toSet()),
        toDependency(dependencyManager.dependencyGraph().allDependencies(), ignoreDependencies),
        allUsedClasses
    );
  }

  /**
   * Returns a set of {@code DependencyCoordinate}s that match given string representations.
   *
   * @param allDependencies    all known dependencies
   * @param dependencyPatterns string representation of dependencies to match
   * @return a set of {@code Dependency} that match given string representations
   */
  private Set<Dependency> toDependency(Set<Dependency> allDependencies, Set<String> dependencyPatterns) {
    System.out.println("allDependencies: ");
    allDependencies.forEach(System.out::println);
    System.out.println("dependencyPatterns: ");
    dependencyPatterns.forEach(System.out::println);

    return dependencyPatterns.stream()
            .flatMap(pattern -> findDependencies(allDependencies, pattern).stream())
            .collect(Collectors.toSet());
  }

  private Set<Dependency> findDependencies(Set<Dependency> allDependencies, String patternString) {
    Pattern pattern = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
    return allDependencies.stream()
            .filter(dep -> pattern.matcher(dep.toString()).matches())
            .collect(Collectors.toSet());
  }

  private String getTime(long millis) {
    long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
    long seconds = (TimeUnit.MILLISECONDS.toSeconds(millis) % 60);
    return String.format("%smin %ss", minutes, seconds);
  }

  private void printString(final String string) {
    System.out.println(string); //NOSONAR avoid a warning of non-used logger
  }

  private LogWrapper getLog() {
    return dependencyManager.getLog();
  }
}
