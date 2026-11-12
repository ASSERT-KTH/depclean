package se.kth.depclean.core.analysis.graph;

import java.util.Collections;
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
  private final Map<String, Set<String>> usagesPerClass;
  private final Set<String> reachableClasses;

  /**
   * Creates a graph.
   *
   * @param projectClasses the classes compiled from the project itself
   * @param usagesPerClass for every analyzed class, the classes it references
   */
  public ClassUsageGraph(Set<String> projectClasses, Map<String, Set<String>> usagesPerClass) {
    this.projectClasses = Collections.unmodifiableSet(projectClasses);
    this.usagesPerClass = Collections.unmodifiableMap(usagesPerClass);
    final Set<String> reachable = new HashSet<>(projectClasses);
    reachable.addAll(usagesPerClass.keySet());
    usagesPerClass.values().forEach(reachable::addAll);
    this.reachableClasses = Collections.unmodifiableSet(reachable);
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
   * The classes of the project, i.e. the starting points of the usage analysis.
   *
   * @return the fully qualified names of the project classes
   */
  public Set<String> getProjectClasses() {
    return projectClasses;
  }

  /**
   * The references between classes. Only classes that the project can reach are analyzed, so only
   * those have an entry.
   *
   * @return for every analyzed class, the fully qualified names of the classes it references
   */
  public Map<String, Set<String>> getUsagesPerClass() {
    return usagesPerClass;
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
   * Whether a class references another one.
   *
   * @param from the fully qualified name of the referencing class
   * @param to the fully qualified name of the referenced class
   * @return true if {@code from} was analyzed and references {@code to}
   */
  public boolean containsEdge(String from, String to) {
    return usagesPerClass.getOrDefault(from, Collections.emptySet()).contains(to);
  }
}
