package se.kth.depclean.core.analysis;

import static com.google.common.collect.Sets.newHashSet;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import se.kth.depclean.core.analysis.model.ProjectDependencyAnalysis;
import se.kth.depclean.core.model.ClassName;
import se.kth.depclean.core.model.Dependency;
import se.kth.depclean.core.model.ProjectContext;
import se.kth.depclean.core.model.Scope;

/**
 * Builds the analysis given the declared dependencies and the one actually used.
 */
@Slf4j
public class ProjectDependencyAnalysisBuilder {

  private final ProjectContext context;
  private final ActualUsedClasses actualUsedClasses;
  private final Set<Dependency> usedDependencies;

  ProjectDependencyAnalysisBuilder(ProjectContext context, ActualUsedClasses actualUsedClasses) {
    this.context = context;
    this.actualUsedClasses = actualUsedClasses;
    usedDependencies = actualUsedClasses.getRegisteredClasses().stream()
        .flatMap(clazz -> context.getDependenciesForClass(clazz).stream())
        .collect(Collectors.toSet());

    log.debug("Actual used classes: " + actualUsedClasses.getRegisteredClasses());
    log.debug("Used dependencies" + usedDependencies);
  }

  /**
   * Analyse the dependencies to find out what is used and what is not.
   *
   * @return the analysis
   */
  public ProjectDependencyAnalysis analyse() {
    // used dependencies
    final Set<Dependency> usedDirectDependencies = getUsedDirectDependencies();
    final Set<Dependency> usedTransitiveDependencies = getUsedTransitiveDependencies();
    final Set<Dependency> usedInheritedDirectDependencies = getUsedInheritedDirectDependencies();
    final Set<Dependency> usedInheritedTransitiveDependencies = getUsedInheritedTransitiveDependencies();
    // unused dependencies
    final Set<Dependency> unusedDirectDependencies = getUnusedDirectDependencies(usedDirectDependencies);
    final Set<Dependency> unusedTransitiveDependencies = getUnusedTransitiveDependencies(usedTransitiveDependencies);
    final Set<Dependency> unusedInheritedDirectDependencies = getUnusedInheritedDirectDependencies(usedInheritedDirectDependencies);
    final Set<Dependency> unusedInheritedTransitiveDependencies = getUnusedInheritedTransitiveDependencies(usedInheritedTransitiveDependencies);
    // classes in each dependency
    final Map<Dependency, DependencyTypes> dependencyClassesMap = buildDependencyClassesMap();
    // ignore dependencies
    context.getIgnoredDependencies().forEach(dependencyToIgnore -> {
      ignoreDependency(usedDirectDependencies, unusedDirectDependencies, dependencyToIgnore);
      ignoreDependency(usedTransitiveDependencies, unusedTransitiveDependencies, dependencyToIgnore);
      ignoreDependency(usedInheritedDirectDependencies, unusedInheritedDirectDependencies, dependencyToIgnore);
      ignoreDependency(usedInheritedTransitiveDependencies, unusedInheritedTransitiveDependencies, dependencyToIgnore);
    });
    // ignore scopes
    ignoreDependencyWithIgnoredScope(usedDirectDependencies, unusedDirectDependencies, context.getIgnoredScopes());
    ignoreDependencyWithIgnoredScope(usedTransitiveDependencies, unusedTransitiveDependencies, context.getIgnoredScopes());
    ignoreDependencyWithIgnoredScope(usedInheritedDirectDependencies, unusedInheritedDirectDependencies, context.getIgnoredScopes());
    ignoreDependencyWithIgnoredScope(usedInheritedTransitiveDependencies, unusedInheritedTransitiveDependencies, context.getIgnoredScopes());

    return new ProjectDependencyAnalysis(
        usedDirectDependencies,
        usedTransitiveDependencies,
        usedInheritedDirectDependencies,
        usedInheritedTransitiveDependencies,
        unusedDirectDependencies,
        unusedTransitiveDependencies,
        unusedInheritedDirectDependencies,
        unusedInheritedTransitiveDependencies,
        context.getIgnoredDependencies(),
        dependencyClassesMap,
        context.getDependencyGraph()
    );
  }

  private Map<Dependency, DependencyTypes> buildDependencyClassesMap() {
    final Map<Dependency, DependencyTypes> output = new HashMap<>();
    final Collection<Dependency> allDependencies = newHashSet(context.getAllDependencies());
    for (Dependency dependency : allDependencies) {
      final Set<ClassName> allClasses = context.getClassesForDependency(dependency);
      final Set<ClassName> usedClasses = newHashSet(allClasses);
      usedClasses.retainAll(actualUsedClasses.getRegisteredClasses());
      output.put(dependency, new DependencyTypes(allClasses, usedClasses));
    }
    return output;
  }

  private Set<Dependency> getUsedInheritedDirectDependencies() {
    return usedDependencies.stream()
        .filter(a -> context.getDependencyGraph().inheritedDirectDependencies().contains(a))
        .peek(dependency -> log.trace("## Used Inherited Direct dependency {}", dependency))
        .collect(Collectors.toSet());
  }

  private Set<Dependency> getUsedDirectDependencies() {
    return usedDependencies.stream()
        .filter(a -> context.getDependencyGraph().directDependencies().contains(a))
        .peek(dependency -> log.trace("## Used Direct dependency {}", dependency))
        .collect(Collectors.toSet());
  }

  private Set<Dependency> getUsedTransitiveDependencies() {
    return usedDependencies.stream()
        .filter(a -> context.getDependencyGraph().transitiveDependencies().contains(a))
        .peek(dependency -> log.trace("## Used Transitive dependency {}", dependency))
        .collect(Collectors.toSet());
  }

  private Set<Dependency> getUsedInheritedTransitiveDependencies() {
    return usedDependencies.stream()
        .filter(a -> context.getDependencyGraph().inheritedTransitiveDependencies().contains(a))
        .peek(dependency -> log.trace("## Used Transitive dependency {}", dependency))
        .collect(Collectors.toSet());
  }

  private Set<Dependency> getUnusedDirectDependencies(Set<Dependency> usedDirectDependencies) {
    return getUnusedDependencies(context.getDependencyGraph().directDependencies(), usedDirectDependencies);
  }

  private Set<Dependency> getUnusedTransitiveDependencies(Set<Dependency> usedTransitiveDependencies) {
    return getUnusedDependencies(context.getDependencyGraph().transitiveDependencies(), usedTransitiveDependencies);
  }

  private Set<Dependency> getUnusedInheritedDirectDependencies(Set<Dependency> usedInheritedDependencies) {
    return getUnusedDependencies(context.getDependencyGraph().inheritedDirectDependencies(), usedInheritedDependencies);
  }

  private Set<Dependency> getUnusedInheritedTransitiveDependencies(Set<Dependency> usedInheritedDependencies) {
    return getUnusedDependencies(context.getDependencyGraph().inheritedTransitiveDependencies(), usedInheritedDependencies);
  }

  private Set<Dependency> getUnusedDependencies(Set<Dependency> baseDependencies, Set<Dependency> usedDependencies) {
    final Set<Dependency> unusedInheritedDependencies = newHashSet(baseDependencies);
    unusedInheritedDependencies.removeAll(usedDependencies);
    return unusedInheritedDependencies;
  }

  /**
   * If the dependency to ignore is an unused dependency, then add it to the set of usedDependencyCoordinates and remove it from the set of
   * unusedDependencyCoordinates.
   *
   * @param usedDependencies   The set of used artifacts where the dependency will be added.
   * @param unusedDependencies The set of unused artifacts where the dependency will be removed.
   * @param dependencyToIgnore The dependency to ignore.
   */
  private void ignoreDependency(Set<Dependency> usedDependencies, Set<Dependency> unusedDependencies, Dependency dependencyToIgnore) {
    for (Iterator<Dependency> i = unusedDependencies.iterator(); i.hasNext(); ) {
      Dependency unusedDependency = i.next();
      if (dependencyToIgnore.equals(unusedDependency)) {
        usedDependencies.add(unusedDependency);
        i.remove();
        break;
      }
    }
  }

  /**
   * If the scope of the unused dependency is to be ignored, then add the dependency to the set of used dependencies and remove it from the used set.
   *
   * @param usedDependencies   The set of used artifacts where the dependency will be added.
   * @param unusedDependencies The set of unused artifacts where the dependency will be removed.
   * @param ignoredScopes      The set of scopes to ignore.
   */
  private void ignoreDependencyWithIgnoredScope(Set<Dependency> usedDependencies, Set<Dependency> unusedDependencies, Set<Scope> ignoredScopes) {
    for (Iterator<Dependency> i = unusedDependencies.iterator(); i.hasNext(); ) {
      Dependency unusedDependency = i.next();
      List<String> scopesToIgnore = ignoredScopes.stream().map(Scope::getValue).collect(Collectors.toList());
      log.debug("Scopes to ignore: {}", scopesToIgnore);
      log.debug("Unused dependency scope: {}", unusedDependency.getScope());
      if (scopesToIgnore.contains(unusedDependency.getScope())) {
        log.debug("Ignoring dependency {} with scope {}", unusedDependency, unusedDependency.getScope());
        usedDependencies.add(unusedDependency);
        i.remove();
      }
    }
  }

}
