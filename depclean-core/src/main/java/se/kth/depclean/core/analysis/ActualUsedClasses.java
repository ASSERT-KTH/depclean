package se.kth.depclean.core.analysis;

import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import se.kth.depclean.core.model.ClassName;
import se.kth.depclean.core.model.ProjectContext;

/**
 * Contains the actual classes used in the project (i.e. in classes, processors, configurations, etc.)
 */
@Slf4j
public class ActualUsedClasses {

  private final ProjectContext context;
  private final Set<ClassName> classes = new HashSet<>();

  public ActualUsedClasses(ProjectContext context) {
    this.context = context;
  }

  private void registerClass(ClassName className) {
    // Do not register class unknown to dependencies
    if (context.hasNoDependencyOnClass(className)) {
      log.debug("Class {} is not known to any dependency", className);
      return;
    }
    log.debug("## Registered class {}", className);
    classes.add(className);
  }

  public void registerClasses(Iterable<ClassName> classes) {
    classes.forEach(this::registerClass);
  }

  public Set<ClassName> getRegisteredClasses() {
    return classes;
  }
}
