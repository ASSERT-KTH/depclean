package se.kth.depclean.core.analysis;

import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Contains the actual classes used in the project (i.e. in classes, processors, configurations, etc.)
 */
@Slf4j
public class ActualUsedClasses {

  final Set<String> classes = new HashSet<>();
  private final DeclaredDependencyGraph declaredDependencyGraph;

  public ActualUsedClasses(DeclaredDependencyGraph declaredDependencyGraph) {
    this.declaredDependencyGraph = declaredDependencyGraph;
  }

  private void registerClass(String clazz) {
    String className = clazz.replace('/', '.');
    if (className.endsWith(".class")) {
      className = className.substring(0, className.length() - ".class".length());
    }

    // Do not register class unknown to dependencies
    if (declaredDependencyGraph.doesntKnow(className)) {
      return;
    }

    log.info("## Register class {} as {}", clazz, className);
    classes.add(className);
  }

  public void registerClasses(Iterable<String> classes) {
    classes.forEach(this::registerClass);
  }

  public Set<String> getRegisteredClasses() {
    return classes;
  }
}
