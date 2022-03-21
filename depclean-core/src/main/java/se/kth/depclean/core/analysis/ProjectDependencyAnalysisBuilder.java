package se.kth.depclean.core.analysis;

import static com.google.common.collect.Sets.newHashSet;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import se.kth.depclean.core.analysis.model.ClassName;
import se.kth.depclean.core.analysis.model.DependencyCoordinate;
import se.kth.depclean.core.analysis.model.ProjectContext;

/**
 * Builds the analysis given the declared dependencies and the one actually used.
 */
@Slf4j
public class ProjectDependencyAnalysisBuilder {

  private final ProjectContext context;
  private final ActualUsedClasses actualUsedClasses;
  private final Set<DependencyCoordinate> usedDependencyCoordinates;

  ProjectDependencyAnalysisBuilder(ProjectContext context,
                                   ActualUsedClasses actualUsedClasses) {
    this.context = context;
    this.actualUsedClasses = actualUsedClasses;
    usedDependencyCoordinates = actualUsedClasses.getRegisteredClasses().stream()
        .flatMap(clazz -> context.getDependenciesForClass(clazz).stream())
        .collect(Collectors.toSet());
  }

  /**
   * Analyse the dependencies to find out what is used and what is not.
   *
   * @return the analysis
   */
  public ProjectDependencyAnalysis analyse() {
    final Set<DependencyCoordinate> usedDirectDependencies =
        getUsedDirectDependencies();
    final Set<DependencyCoordinate> usedTransitiveDependencies =
        getUsedTransitiveDependencies();
    final Set<DependencyCoordinate> usedInheritedDependencies =
        getUsedInheritedDependencies();
    final Set<DependencyCoordinate> unusedDirectDependencies =
        getUnusedDirectDependencies(usedDirectDependencies);
    final Set<DependencyCoordinate> unusedTransitiveDependencies =
        getUnusedTransitiveDependencies(usedTransitiveDependencies);
    final Set<DependencyCoordinate> unusedInheritedDependencies =
        getUnusedInheritedDependencies(usedInheritedDependencies);
    final Map<DependencyCoordinate, DependencyTypes> dependencyClassesMap =
        buildDependencyClassesMap();

    context.getIgnoredDependencies().forEach(dependencyToIgnore -> {
      ignoreDependency(usedDirectDependencies, unusedDirectDependencies, dependencyToIgnore);
      ignoreDependency(usedTransitiveDependencies, unusedTransitiveDependencies, dependencyToIgnore);
      ignoreDependency(usedInheritedDependencies, unusedInheritedDependencies, dependencyToIgnore);
    });

    return new ProjectDependencyAnalysis(
        usedDirectDependencies,
        usedTransitiveDependencies,
        usedInheritedDependencies,
        unusedDirectDependencies,
        unusedTransitiveDependencies,
        unusedInheritedDependencies,
        dependencyClassesMap);
  }

  private Map<DependencyCoordinate, DependencyTypes> buildDependencyClassesMap() {
    final Map<DependencyCoordinate, DependencyTypes> output = new HashMap<>();
    final Collection<DependencyCoordinate> allDependencies = newHashSet(context.getAllDependencies());
    allDependencies.add(context.getProjectCoordinates());
    for (DependencyCoordinate dependency : allDependencies) {
      final Set<ClassName> allClasses = context.getClassesForDependency(dependency);
      final Set<ClassName> usedClasses = newHashSet(allClasses);
      usedClasses.retainAll(actualUsedClasses.getRegisteredClasses());
      output.put(dependency, new DependencyTypes(allClasses, usedClasses));
    }
    return output;
  }

  private Set<DependencyCoordinate> getUsedDirectDependencies() {
    return usedDependencyCoordinates.stream()
        .filter(a -> context.getDirectDependencies().contains(a))
        //.peek(DependencyCoordinate -> log.debug("## Used Direct dependency {}", DependencyCoordinate))
        .collect(Collectors.toSet());
  }

  private Set<DependencyCoordinate> getUsedTransitiveDependencies() {
    return usedDependencyCoordinates.stream()
        .filter(a -> context.getTransitiveDependencies().contains(a))
        //.peek(DependencyCoordinate -> log.debug("## Used Transitive dependency {}", DependencyCoordinate))
        .collect(Collectors.toSet());
  }

  private Set<DependencyCoordinate> getUsedInheritedDependencies() {
    return usedDependencyCoordinates.stream()
        .filter(a -> context.getInheritedDependencies().contains(a))
        //.peek(DependencyCoordinate -> log.debug("## Used Transitive dependency {}", DependencyCoordinate))
        .collect(Collectors.toSet());
  }

  private Set<DependencyCoordinate> getUnusedDirectDependencies(Set<DependencyCoordinate> usedDirectDependencies) {
    return getUnusedDependencies(context.getDirectDependencies(), usedDirectDependencies);
  }

  private Set<DependencyCoordinate> getUnusedTransitiveDependencies(
      Set<DependencyCoordinate> usedTransitiveDependencies) {
    return getUnusedDependencies(context.getTransitiveDependencies(), usedTransitiveDependencies);
  }

  private Set<DependencyCoordinate> getUnusedInheritedDependencies(
      Set<DependencyCoordinate> usedInheritedDependencies) {
    return getUnusedDependencies(context.getInheritedDependencies(), usedInheritedDependencies);
  }

  private Set<DependencyCoordinate> getUnusedDependencies(
      Set<DependencyCoordinate> baseDependencies, Set<DependencyCoordinate> usedDependencies) {
    final Set<DependencyCoordinate> unusedInheritedDependencies = newHashSet(baseDependencies);
    unusedInheritedDependencies.removeAll(usedDependencies);
    return unusedInheritedDependencies;
  }

  /**
   * If the dependencyToIgnore is an unused dependency, then add it to the set of usedDependencyCoordinates and remove
   * it from the set of unusedDependencyCoordinates.
   *
   * @param usedDependencyCoordinates   The set of used artifacts where the dependency will be added.
   * @param unusedDependencyCoordinates The set of unused artifacts where the dependency will be removed.
   * @param dependencyToIgnore         The dependency to ignore.
   */
  private void ignoreDependency(
      Set<DependencyCoordinate> usedDependencyCoordinates,
      Set<DependencyCoordinate> unusedDependencyCoordinates,
      DependencyCoordinate dependencyToIgnore) {

    for (Iterator<DependencyCoordinate> i = unusedDependencyCoordinates.iterator(); i.hasNext(); ) {
      DependencyCoordinate unusedDependency = i.next();
      if (dependencyToIgnore.equals(unusedDependency)) {
        usedDependencyCoordinates.add(unusedDependency);
        i.remove();
        break;
      }
    }
  }
}
