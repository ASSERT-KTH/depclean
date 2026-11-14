package se.kth.depclean.core.model;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import java.nio.file.Path;
import java.util.Collections;
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

  private final Set<Scope> ignoredScopes;
  private final Set<Dependency> ignoredDependencies;
  private final Set<ClassName> extraClasses;
  private final Set<ClassName> extraTestClasses;
  private final DependencyGraph dependencyGraph;

  /**
   * Creates a new project context.
   *
   * @param dependencyGraph the dependencyGraph
   * @param outputFolders where the project's classes are compiled
   * @param testOutputFolders where the project's test classes are compiled
   * @param ignoredScopes the scopes to ignore
   * @param ignoredDependencies the dependencies to ignore (i.e. considered as 'used')
   * @param extraClasses some classes we want to tell the analyser to consider used (like maven
   *     processors)
   */
  public ProjectContext(
      DependencyGraph dependencyGraph,
      Set<Path> outputFolders,
      Set<Path> testOutputFolders,
      Set<Scope> ignoredScopes,
      Set<Dependency> ignoredDependencies,
      Set<ClassName> extraClasses) {
    this(
        dependencyGraph,
        outputFolders,
        testOutputFolders,
        ignoredScopes,
        ignoredDependencies,
        extraClasses,
        Collections.emptySet());
  }

  /**
   * Creates a new project context.
   *
   * @param dependencyGraph the dependencyGraph
   * @param outputFolders where the project's classes are compiled
   * @param testOutputFolders where the project's test classes are compiled
   * @param ignoredScopes the scopes to ignore
   * @param ignoredDependencies the dependencies to ignore (i.e. considered as 'used')
   * @param extraClasses classes that the main code uses although its bytecode does not reference
   *     them (e.g. classes named in configuration files)
   * @param extraTestClasses classes that only the tests use although their bytecode does not
   *     reference them
   */
  public ProjectContext(
      DependencyGraph dependencyGraph,
      Set<Path> outputFolders,
      Set<Path> testOutputFolders,
      Set<Scope> ignoredScopes,
      Set<Dependency> ignoredDependencies,
      Set<ClassName> extraClasses,
      Set<ClassName> extraTestClasses) {
    this.extraTestClasses = extraTestClasses;
    this.dependencyGraph = dependencyGraph;
    this.outputFolders = outputFolders;
    this.testOutputFolders = testOutputFolders;
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

  public Set<Scope> getIgnoredScopes() {
    return ignoredScopes;
  }

  public Set<Dependency> getIgnoredDependencies() {
    return ignoredDependencies;
  }

  public Set<ClassName> getExtraClasses() {
    return extraClasses;
  }

  public Set<ClassName> getExtraTestClasses() {
    return extraTestClasses;
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
        .forEach(dc -> {
          log.debug("Adding dependency {} with related classes: {}", dc, dc.getRelatedClasses());
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
    log.debug("ignoreScopes: {}", ignoredScopes);
    log.debug("dc = {} declaredScope = {}", dc, declaredScope);
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
