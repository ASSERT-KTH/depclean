package se.kth.depclean.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;
import se.kth.depclean.core.analysis.AnalysisFailureException;
import se.kth.depclean.core.analysis.DefaultProjectDependencyAnalyzer;
import se.kth.depclean.core.analysis.graph.DependencyGraph;
import se.kth.depclean.core.analysis.model.ProjectDependencyAnalysis;
import se.kth.depclean.core.analysis.src.ImportsAnalyzer;
import se.kth.depclean.core.analysis.src.XmlClassesAnalyzer;
import se.kth.depclean.core.model.ClassName;
import se.kth.depclean.core.model.Dependency;
import se.kth.depclean.core.model.ProjectContext;
import se.kth.depclean.core.model.Scope;
import se.kth.depclean.core.wrapper.DependencyManagerWrapper;
import se.kth.depclean.core.wrapper.LogWrapper;

/** Runs the depclean process, regardless of a specific dependency manager. */
public class DepCleanManager {

  private static final String SEPARATOR = "-------------------------------------------------------";

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

  /** Creates the DepClean manager. */
  @SuppressWarnings(
      "java:S107") // the parameters mirror the reported analysis dimensions one-to-one
  public DepCleanManager(
      DependencyManagerWrapper dependencyManager,
      boolean skipDepClean,
      boolean ignoreTests,
      Set<String> ignoreScopes,
      Set<String> ignoreDependencies,
      boolean failIfUnusedDirect,
      boolean failIfUnusedTransitive,
      boolean failIfUnusedInheritedDirect,
      boolean failIfUnusedInheritedTransitive,
      boolean createPomDebloated,
      boolean createResultJson,
      boolean createCallGraphCsv) {
    this.dependencyManager = dependencyManager;
    this.skipDepClean = skipDepClean;
    this.ignoreTests = ignoreTests;
    // Defensive copy: buildProjectContext() adds the "test" scope when tests are ignored
    this.ignoreScopes = new HashSet<>(ignoreScopes);
    this.ignoreDependencies = ignoreDependencies;
    this.failIfUnusedDirect = failIfUnusedDirect;
    this.failIfUnusedTransitive = failIfUnusedTransitive;
    this.failIfUnusedInheritedDirect = failIfUnusedInheritedDirect;
    this.failIfUnusedInheritedTransitive = failIfUnusedInheritedTransitive;
    this.createPomDebloated = createPomDebloated;
    this.createResultJson = createResultJson;
    this.createCallGraphCsv = createCallGraphCsv;
  }

  /** Execute the depClean manager. */
  @Nullable
  public ProjectDependencyAnalysis execute() throws AnalysisFailureException, IOException {
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

    long phaseStart = System.currentTimeMillis();
    final ProjectContext projectContext = buildProjectContext();
    phaseStart = logPhase("Building project context", phaseStart);

    final DefaultProjectDependencyAnalyzer projectDependencyAnalyzer =
        new DefaultProjectDependencyAnalyzer();
    final ProjectDependencyAnalysis analysis = projectDependencyAnalyzer.analyze(projectContext);
    phaseStart = logPhase("Analyzing bytecode", phaseStart);
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
      phaseStart = System.currentTimeMillis();
      dependencyManager.getDebloater(analysis).write();
      logPhase("Writing debloated pom", phaseStart);
    }

    /* Writing the JSON file with the depclean results */
    if (createResultJson) {
      phaseStart = System.currentTimeMillis();
      createResultJson(analysis);
      logPhase("Writing result files", phaseStart);
    }

    final long stopTime = System.currentTimeMillis();
    getLog().info("Analysis done in " + getTime(stopTime - startTime));

    return analysis;
  }

  private void createResultJson(ProjectDependencyAnalysis analysis) {
    printString("Creating depclean-results.json, please wait...");
    final File jsonFile =
        new File(dependencyManager.getBuildDirectory() + File.separator + "depclean-results.json");
    final File csvFile =
        new File(dependencyManager.getBuildDirectory() + File.separator + "depclean-callgraph.csv");
    if (createCallGraphCsv) {
      printString("Creating " + csvFile.getName() + ", please wait...");
      try {
        FileUtils.write(
            csvFile, "OriginClass,TargetClass,TargetDependency\n", StandardCharsets.UTF_8);
      } catch (IOException e) {
        getLog().error("Error writing the CSV header.");
      }
    }
    String treeAsJson = dependencyManager.getTreeAsJson(analysis, csvFile, createCallGraphCsv);

    try {
      FileUtils.write(jsonFile, treeAsJson, StandardCharsets.UTF_8);
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
    // Resolved once: building the graph lists the classes of every dependency
    final DependencyGraph dependencyGraph = dependencyManager.dependencyGraph();
    if (ignoreTests) {
      ignoreScopes.add("test");
    }

    // Classes that the main code uses without referencing them in its bytecode: the annotation
    // processors of the build, the imports of the sources (which also cover what the compiler
    // inlines or erases, such as constants and source-retention annotations) and the classes
    // named in XML resources (e.g. Spring XML configurations, web.xml, persistence.xml)
    final Set<ClassName> extraClasses = new HashSet<>();
    dependencyManager.collectUsedClassesFromProcessors().stream()
        .map(ClassName::new)
        .forEach(extraClasses::add);
    extraClasses.addAll(importedClasses(dependencyManager.getSourceDirectory()));
    extraClasses.addAll(xmlReferencedClasses(dependencyManager.getResourcesDirectories()));

    // The same for the tests
    final Set<ClassName> extraTestClasses =
        new HashSet<>(importedClasses(dependencyManager.getTestDirectory()));
    if (!ignoreTests) {
      extraTestClasses.addAll(
          xmlReferencedClasses(dependencyManager.getTestResourcesDirectories()));
    }

    return new ProjectContext(
        dependencyGraph,
        dependencyManager.getOutputDirectories(),
        dependencyManager.getTestOutputDirectories(),
        ignoreScopes.stream().map(scope -> new Scope(scope)).collect(Collectors.toSet()),
        ignoreDependencies.stream()
            .map(pattern -> toDependency(dependencyGraph.allDependencies(), pattern))
            .flatMap(Set::stream)
            .collect(Collectors.toSet()),
        extraClasses,
        extraTestClasses);
  }

  private static Set<ClassName> importedClasses(Path sourceDirectory) {
    return new ImportsAnalyzer(sourceDirectory)
        .collectImportedClassesFromSource().stream()
            .map(ClassName::new)
            .collect(Collectors.toSet());
  }

  private static Set<ClassName> xmlReferencedClasses(Set<Path> resourcesDirectories) {
    return resourcesDirectories.stream()
        .map(XmlClassesAnalyzer::new)
        .map(XmlClassesAnalyzer::collectReferencedClassesFromXml)
        .flatMap(Set::stream)
        .map(ClassName::new)
        .collect(Collectors.toSet());
  }

  /**
   * Returns a set of {@code DependencyCoordinate}s that match given string representations.
   *
   * @param allDependencies all known dependencies
   * @param patternString string representation of dependencies to match
   * @return a set of {@code Dependency} that match given string representations
   */
  private Set<Dependency> toDependency(Set<Dependency> allDependencies, String patternString) {
    Pattern pattern = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
    return allDependencies.stream()
        .filter(dep -> pattern.matcher(dep.toString()).matches())
        .collect(Collectors.toSet());
  }

  private String getTime(long millis) {
    long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
    long seconds = (TimeUnit.MILLISECONDS.toSeconds(millis) % 60);
    return minutes + "min " + seconds + "s";
  }

  /** Logs the duration of a phase that started at {@code start} and returns the current time. */
  private long logPhase(String phase, long start) {
    final long now = System.currentTimeMillis();
    getLog().info(String.format(Locale.ROOT, "%s took %.1fs", phase, (now - start) / 1000.0));
    return now;
  }

  private void printString(final String string) {
    getLog().info(string);
  }

  private LogWrapper getLog() {
    return dependencyManager.getLog();
  }
}
