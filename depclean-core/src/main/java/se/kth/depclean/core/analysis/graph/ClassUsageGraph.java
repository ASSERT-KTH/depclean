package se.kth.depclean.core.analysis.graph;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The classes that a project uses, as found by following the references in the bytecode: from the
 * classes of the project to the classes they reference, to the classes those reference, and so on.
 */
public final class ClassUsageGraph {

  private static final ClassUsageGraph EMPTY =
      new ClassUsageGraph(Collections.emptySet(), Collections.emptyMap());

  private final Set<String> projectClasses;
  private final Set<String> mainEntryPoints;
  private final Map<String, Set<String>> usagesPerClass;
  private final Map<String, Set<String>> serviceProviders;
  private final Set<String> reachableClasses;

  /**
   * Creates a graph in which every class of the project belongs to its main code.
   *
   * @param projectClasses the classes compiled from the project itself
   * @param usagesPerClass for every analyzed class, the classes it references
   */
  public ClassUsageGraph(Set<String> projectClasses, Map<String, Set<String>> usagesPerClass) {
    this(projectClasses, projectClasses, projectClasses, usagesPerClass, Collections.emptyMap());
  }

  /**
   * Creates a graph.
   *
   * @param projectClasses the classes compiled from the project itself, tests included
   * @param mainEntryPoints the classes the main code starts from: its own classes, and the classes
   *     it uses without referencing them in the bytecode
   * @param entryPoints the classes the whole project starts from, a superset of {@code
   *     mainEntryPoints}
   * @param usagesPerClass for every analyzed class, the classes it references
   * @param serviceProviders for every reachable service type, the implementations registered for it
   *     on the classpath
   */
  public ClassUsageGraph(
      Set<String> projectClasses,
      Set<String> mainEntryPoints,
      Set<String> entryPoints,
      Map<String, Set<String>> usagesPerClass,
      Map<String, Set<String>> serviceProviders) {
    this.projectClasses = Collections.unmodifiableSet(projectClasses);
    this.mainEntryPoints = Collections.unmodifiableSet(mainEntryPoints);
    this.usagesPerClass = Collections.unmodifiableMap(usagesPerClass);
    this.serviceProviders = Collections.unmodifiableMap(serviceProviders);
    this.reachableClasses = Collections.unmodifiableSet(reachableFrom(entryPoints));
  }

  /**
   * A graph without any class.
   *
   * @return the empty graph
   */
  public static ClassUsageGraph empty() {
    return EMPTY;
  }

  /**
   * The classes of the project, tests included.
   *
   * @return the fully qualified names of the project classes
   */
  public Set<String> getProjectClasses() {
    return projectClasses;
  }

  /**
   * The references between classes. Only classes that the project can reach are analyzed, so only
   * those have an entry. The implementations of a service count as references of the service type.
   *
   * @return for every analyzed class, the fully qualified names of the classes it references
   */
  public Map<String, Set<String>> getUsagesPerClass() {
    return usagesPerClass;
  }

  /**
   * The service types the project can reach for which the classpath registers implementations in
   * {@code META-INF/services}.
   *
   * @return for every such service type, the fully qualified names of its implementations
   */
  public Map<String, Set<String>> getServiceProviders() {
    return serviceProviders;
  }

  /**
   * Every class the project can reach, directly or through other classes. This includes the classes
   * of the project and classes that are not on the analyzed classpath, such as the ones of the JDK.
   *
   * @return the fully qualified names of the reachable classes
   */
  public Set<String> getReachableClasses() {
    return reachableClasses;
  }

  /**
   * Every class the main code of the project can reach, i.e. without going through its tests.
   *
   * @return the fully qualified names of the classes reachable from the main code
   */
  public Set<String> getClassesReachableFromMain() {
    return reachableFrom(mainEntryPoints);
  }

  /**
   * Whether a class references another one.
   *
   * @param from the fully qualified name of the referencing class
   * @param to the fully qualified name of the referenced class
   * @return true if {@code from} was analyzed and references {@code to}
   */
  public boolean containsEdge(String from, String to) {
    return usagesPerClass.getOrDefault(from, Collections.emptySet()).contains(to);
  }

  private Set<String> reachableFrom(Collection<String> starts) {
    final Set<String> visited = new HashSet<>();
    final Deque<String> pending = new ArrayDeque<>(starts);
    while (!pending.isEmpty()) {
      final String current = pending.pop();
      if (visited.add(current)) {
        for (String reference : usagesPerClass.getOrDefault(current, Collections.emptySet())) {
          if (!visited.contains(reference)) {
            pending.push(reference);
          }
        }
      }
    }
    return visited;
  }
}
