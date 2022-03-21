package se.kth.depclean.core.analysis.model;

import static com.google.common.collect.ImmutableSet.copyOf;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * Contains all information about the project's context, without any reference
 * to a given framework (Maven, Gradle, etc.).
 */
@Slf4j
@Getter
@ToString
@EqualsAndHashCode
public final class ProjectContext {

  private final Multimap<DependencyCoordinate, ClassName> classesPerDependency = ArrayListMultimap.create();
  private final Multimap<ClassName, DependencyCoordinate> dependenciesPerClass = ArrayListMultimap.create();

  private final DependencyCoordinate projectCoordinates;
  private final Set<DependencyCoordinate> directDependencies;
  private final Set<DependencyCoordinate> inheritedDependencies;
  private final Set<DependencyCoordinate> transitiveDependencies;

  private final Path outputFolder;
  private final Path testOutputFolder;

  private final Set<Scope> ignoredScopes;
  private final Set<DependencyCoordinate> ignoredDependencies;

  private final Set<ClassName> extraClasses;

  /**
   * Creates a new project context.
   *
   * @param projectCoordinates the coordinates (groupId:dependencyId:version) of the current project
   * @param directDependencies explicitly declared dependencies
   * @param inheritedDependencies direct dependencies inherited from parent
   * @param transitiveDependencies transitive (implicit) dependencies
   * @param outputFolder where the project's classes are compiled
   * @param testOutputFolder where the project's test classes are compiled
   * @param ignoredScopes the scopes to ignore
   * @param ignoredDependencies the dependencies to ignore (i.e. considered as 'used')
   * @param extraClasses some classes we want to tell the analyser to consider used
   *                     (like maven processors for instance)
   */
  public ProjectContext(DependencyCoordinate projectCoordinates,
                        Set<DependencyCoordinate> directDependencies, Set<DependencyCoordinate> inheritedDependencies,
                        Set<DependencyCoordinate> transitiveDependencies, Path outputFolder, Path testOutputFolder,
                        Set<Scope> ignoredScopes,
                        Set<DependencyCoordinate> ignoredDependencies,
                        Set<ClassName> extraClasses) {
    this.projectCoordinates = projectCoordinates;
    this.directDependencies = directDependencies;
    this.inheritedDependencies = inheritedDependencies;
    this.transitiveDependencies = transitiveDependencies;
    this.outputFolder = outputFolder;
    this.testOutputFolder = testOutputFolder;
    this.ignoredScopes = ignoredScopes;
    this.ignoredDependencies = ignoredDependencies;
    this.extraClasses = extraClasses;

    ignoredScopes.forEach(scope -> log.info("Ignoring scope {}", scope));

    populateDependenciesAndClassesMap(directDependencies);
    populateDependenciesAndClassesMap(inheritedDependencies);
    populateDependenciesAndClassesMap(transitiveDependencies);

    Multimaps.invertFrom(classesPerDependency, dependenciesPerClass);

    if (log.isDebugEnabled()) {
      log.debug("# Direct dependencies");
      directDependencies.forEach(dependency -> log.debug("## Found dependency {}", dependency));
      log.debug("# Inherited dependencies");
      inheritedDependencies.forEach(dependency -> log.debug("## Found dependency {}", dependency));
      log.debug("# Transitive dependencies");
      transitiveDependencies.forEach(dependency -> log.debug("## Found dependency {}", dependency));
    }
  }

  private void populateDependenciesAndClassesMap(Set<DependencyCoordinate> dependencies) {
    dependencies.stream()
        .filter(this::filterScopesIfNeeded)
        .forEach(dc -> classesPerDependency.putAll(dc, dc.getRelatedClasses()));
  }

  private boolean filterScopesIfNeeded(DependencyCoordinate dc) {
    final String declaredScope = dc.getScope();
    return ignoredScopes.stream()
        .map(Scope::getValue)
        .noneMatch(declaredScope::equalsIgnoreCase);
  }

  public Set<ClassName> getClassesForDependency(DependencyCoordinate dependency) {
    return copyOf(classesPerDependency.get(dependency));
  }

  public Set<DependencyCoordinate> getDependenciesForClass(ClassName className) {
    return copyOf(dependenciesPerClass.get(className));
  }

  public boolean hasNoDependencyOnClass(ClassName className) {
    return Iterables.isEmpty(getDependenciesForClass(className));
  }

  public Collection<DependencyCoordinate> getAllDependencies() {
    return classesPerDependency.keys();
  }

  public boolean ignoreTests() {
    return ignoredScopes.contains(new Scope("test"));
  }
}
