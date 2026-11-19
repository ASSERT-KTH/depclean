package se.kth.depclean.core.analysis;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import se.kth.depclean.core.analysis.graph.ClassUsageGraph;
import se.kth.depclean.core.analysis.model.DependencyUsage;
import se.kth.depclean.core.model.ClassName;
import se.kth.depclean.core.model.Dependency;
import se.kth.depclean.core.model.ExtraClassSource;
import se.kth.depclean.core.model.ProjectContext;

/**
 * Explains why dependencies are used, from the references the analysis followed. A reference to a
 * class of a dependency is evidence for that dependency, classified by where it comes from: a class
 * of the project (main code or tests), a class of another dependency, the registration of a service
 * provider, or a class found outside the bytecode (XML resource, import, annotation processor).
 *
 * <p>Every reference is visited once, so the cost is linear in the size of the class usage graph.
 */
public final class DependencyUsageExplainer {

  private final ProjectContext context;
  private final ClassUsageGraph graph;
  private final Set<Dependency> usedDependencies;
  private final Map<String, Set<Dependency>> ownersOfClass = new HashMap<>();
  private final Map<Dependency, DependencyUsage.Builder> usages = new HashMap<>();

  private DependencyUsageExplainer(
      ProjectContext context, ClassUsageGraph graph, Set<Dependency> usedDependencies) {
    this.context = context;
    this.graph = graph;
    this.usedDependencies = usedDependencies;
  }

  /**
   * Explains why each used dependency is used.
   *
   * @param context the analysed project
   * @param graph the references the analysis followed
   * @param usedDependencies the dependencies to explain; the others are left out
   * @param ignoredDependencies the dependencies the user asked to consider used, by coordinates or
   *     scope
   * @return for every used dependency, the evidence found for it
   */
  public static Map<Dependency, DependencyUsage> explain(
      ProjectContext context,
      ClassUsageGraph graph,
      Set<Dependency> usedDependencies,
      Set<Dependency> ignoredDependencies) {
    final DependencyUsageExplainer explainer =
        new DependencyUsageExplainer(context, graph, usedDependencies);
    explainer.addReferences();
    explainer.addExtraClasses();
    for (Dependency dependency : ignoredDependencies) {
      if (usedDependencies.contains(dependency)) {
        explainer.usageOf(dependency).ignored();
      }
    }
    for (Dependency dependency : usedDependencies) {
      explainer.usageOf(dependency);
    }
    final Map<Dependency, DependencyUsage> result = new HashMap<>();
    explainer.usages.forEach((dependency, usage) -> result.put(dependency, usage.build()));
    return Collections.unmodifiableMap(result);
  }

  private void addReferences() {
    final Set<String> projectClasses = graph.getProjectClasses();
    final Set<String> mainEntryPoints = graph.getMainEntryPoints();
    for (Map.Entry<String, Set<String>> references : graph.getUsagesPerClass().entrySet()) {
      final String origin = references.getKey();
      final Set<String> providers =
          graph.getServiceProviders().getOrDefault(origin, Collections.emptySet());
      final boolean fromProject = projectClasses.contains(origin);
      final Set<Dependency> originOwners = fromProject ? Collections.emptySet() : ownersOf(origin);
      for (String target : references.getValue()) {
        for (Dependency owner : ownersOf(target)) {
          if (!usedDependencies.contains(owner)) {
            continue;
          }
          if (providers.contains(target)) {
            usageOf(owner).service(origin);
          } else if (fromProject) {
            if (mainEntryPoints.contains(origin)) {
              usageOf(owner).mainClass(origin);
            } else {
              usageOf(owner).testClass(origin);
            }
          } else {
            for (Dependency originOwner : originOwners) {
              if (!originOwner.equals(owner)) {
                usageOf(owner).dependency(coordinates(originOwner));
              }
            }
          }
        }
      }
    }
  }

  private void addExtraClasses() {
    for (Map.Entry<ClassName, Set<ExtraClassSource>> extraClass :
        context.getExtraClassSources().entrySet()) {
      final String className = extraClass.getKey().getValue();
      for (Dependency owner : ownersOf(className)) {
        if (!usedDependencies.contains(owner)) {
          continue;
        }
        for (ExtraClassSource source : extraClass.getValue()) {
          switch (source) {
            case ANNOTATION_PROCESSOR:
              usageOf(owner).annotationProcessor(className);
              break;
            case IMPORT:
              usageOf(owner).importedClass(className);
              break;
            case XML_RESOURCE:
              usageOf(owner).xmlResourceClass(className);
              break;
            default:
              throw new IllegalStateException("Unknown source " + source);
          }
        }
      }
    }
  }

  private DependencyUsage.Builder usageOf(Dependency dependency) {
    return usages.computeIfAbsent(dependency, k -> DependencyUsage.builder());
  }

  private Set<Dependency> ownersOf(String className) {
    return ownersOfClass.computeIfAbsent(
        className, name -> context.getDependenciesForClass(new ClassName(name)));
  }

  private static String coordinates(Dependency dependency) {
    return dependency.getGroupId()
        + ":"
        + dependency.getDependencyId()
        + ":"
        + dependency.getVersion();
  }
}
