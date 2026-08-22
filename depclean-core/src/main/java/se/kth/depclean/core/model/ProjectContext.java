package se.kth.depclean.core.model;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.depclean.core.analysis.graph.DependencyGraph;

/**
 * Contains all information about the project's context. It doesn't have any reference to a given
 * framework (Maven, Gradle, etc.).
 */
public final class ProjectContext {

  private static final Logger log = LoggerFactory.getLogger(ProjectContext.class);

  private final ListMultimap<Dependency, ClassName> classesPerDependency =
      ArrayListMultimap.create();
  private final ListMultimap<ClassName, Dependency> dependenciesPerClass =
      ArrayListMultimap.create();

  private final Set<Path> outputFolders;
  private final Set<Path> testOutputFolders;
  private final Path sourceFolder;
  private final Path testFolder;
  private final Path dependenciesFolder;

  private final Set<Scope> ignoredScopes;
  private final Set<Dependency> ignoredDependencies;
  private final Set<ClassName> extraClasses;
  private final DependencyGraph dependencyGraph;

  /**
   * Creates a new project context.
   *
   * @param dependencyGraph the dependencyGraph
   * @param outputFolders where the project's classes are compiled
   * @param testOutputFolders where the project's test classes are compiled
   * @param sourceFolder where the project's source code are located
   * @param tesSourceFolder where the project's test sources are located
   * @param dependenciesFolder where the dependency classes are located
   * @param ignoredScopes the scopes to ignore
   * @param ignoredDependencies the dependencies to ignore (i.e. considered as 'used')
   * @param extraClasses some classes we want to tell the analyser to consider used
   */
  public ProjectContext(
      DependencyGraph dependencyGraph,
      Set<Path> outputFolders,
      Set<Path> testOutputFolders,
      Path sourceFolder,
      Path tesSourceFolder,
      Path dependenciesFolder,
      Set<Scope> ignoredScopes,
      Set<Dependency> ignoredDependencies,
      Set<ClassName> extraClasses) {
    this.dependencyGraph = dependencyGraph;
    this.outputFolders = outputFolders;
    this.testOutputFolders = testOutputFolders;
    this.sourceFolder = sourceFolder;
    this.testFolder = tesSourceFolder;
    this.dependenciesFolder = dependenciesFolder;
    this.ignoredScopes = ignoredScopes;
    this.ignoredDependencies = ignoredDependencies;
    this.extraClasses = extraClasses;

    ignoredScopes.forEach(scope -> log.info("Ignoring scope {}", scope));

    populateDependenciesAndClassesMap(dependencyGraph.directDependencies());
    populateDependenciesAndClassesMap(dependencyGraph.inheritedDirectDependencies());
    populateDependenciesAndClassesMap(dependencyGraph.inheritedTransitiveDependencies());
    populateDependenciesAndClassesMap(dependencyGraph.transitiveDependencies());

    Multimaps.invertFrom(classesPerDependency, dependenciesPerClass);
  }

  public Set<Path> getOutputFolders() {
    return outputFolders;
  }

  public Set<Path> getTestOutputFolders() {
    return testOutputFolders;
  }

  public Path getSourceFolder() {
    return sourceFolder;
  }

  public Path getTestFolder() {
    return testFolder;
  }

  public Path getDependenciesFolder() {
    return dependenciesFolder;
  }

  public Set<Scope> getIgnoredScopes() {
    return ignoredScopes;
  }

  public Set<Dependency> getIgnoredDependencies() {
    return ignoredDependencies;
  }

  public Set<ClassName> getExtraClasses() {
    return extraClasses;
  }

  public DependencyGraph getDependencyGraph() {
    return dependencyGraph;
  }

  public Set<ClassName> getClassesForDependency(Dependency dependency) {
    return ImmutableSet.copyOf(classesPerDependency.get(dependency));
  }

  public Set<Dependency> getDependenciesForClass(ClassName className) {
    return ImmutableSet.copyOf(dependenciesPerClass.get(className));
  }

  public boolean hasNoDependencyOnClass(ClassName className) {
    return Iterables.isEmpty(getDependenciesForClass(className));
  }

  /**
   * Get all known dependencies.
   *
   * @return all known dependencies
   */
  public Set<Dependency> getAllDependencies() {
    final Set<Dependency> dependencies = new HashSet<>(dependencyGraph.allDependencies());
    dependencies.add(dependencyGraph.projectCoordinates());
    return ImmutableSet.copyOf(dependencies);
  }

  public boolean ignoreTests() {
    return ignoredScopes.contains(new Scope("test"));
  }

  private void populateDependenciesAndClassesMap(Set<Dependency> dependencies) {
    dependencies.stream()
        .filter(this::excludeDependenciesBasedOnIgnoredScopes)
        .forEach(
            dc -> {
              log.debug(
                  "Adding dependency {} with related classes: {}", dc, dc.getRelatedClasses());
              classesPerDependency.putAll(dc, dc.getRelatedClasses());
            });
  }

  /**
   * Exclude dependencies based on the scopes.
   *
   * @param dc the dependency to check
   * @return true if the dependency should be excluded, false otherwise
   */
  private boolean excludeDependenciesBasedOnIgnoredScopes(Dependency dc) {
    final String declaredScope = dc.getScope();
    log.debug("ignoreScopes: " + ignoredScopes);
    log.debug("dc = " + dc + " declaredScope = " + declaredScope);
    if (declaredScope == null) {
      return true; // Don't exclude if scope is null
    }
    return ignoredScopes.stream().map(Scope::value).noneMatch(declaredScope::equalsIgnoreCase);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ProjectContext that = (ProjectContext) o;
    return Objects.equals(classesPerDependency, that.classesPerDependency)
        && Objects.equals(dependenciesPerClass, that.dependenciesPerClass)
        && Objects.equals(outputFolders, that.outputFolders)
        && Objects.equals(testOutputFolders, that.testOutputFolders)
        && Objects.equals(sourceFolder, that.sourceFolder)
        && Objects.equals(testFolder, that.testFolder)
        && Objects.equals(dependenciesFolder, that.dependenciesFolder)
        && Objects.equals(ignoredScopes, that.ignoredScopes)
        && Objects.equals(ignoredDependencies, that.ignoredDependencies)
        && Objects.equals(extraClasses, that.extraClasses)
        && Objects.equals(dependencyGraph, that.dependencyGraph);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        classesPerDependency,
        dependenciesPerClass,
        outputFolders,
        testOutputFolders,
        sourceFolder,
        testFolder,
        dependenciesFolder,
        ignoredScopes,
        ignoredDependencies,
        extraClasses,
        dependencyGraph);
  }

  @Override
  public String toString() {
    return "ProjectContext(classesPerDependency="
        + classesPerDependency
        + ", dependenciesPerClass="
        + dependenciesPerClass
        + ", outputFolders="
        + outputFolders
        + ", testOutputFolders="
        + testOutputFolders
        + ", sourceFolder="
        + sourceFolder
        + ", testFolder="
        + testFolder
        + ", dependenciesFolder="
        + dependenciesFolder
        + ", ignoredScopes="
        + ignoredScopes
        + ", ignoredDependencies="
        + ignoredDependencies
        + ", extraClasses="
        + extraClasses
        + ", dependencyGraph="
        + dependencyGraph
        + ")";
  }
}
