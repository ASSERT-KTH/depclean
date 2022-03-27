package se.kth.depclean.core.analysis;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import se.kth.depclean.core.model.ClassName;

/**
 * POJO containing the types in a dependency.
 */
@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class DependencyTypes {

  /**
   * An iterable to store the types.
   */
  private Set<ClassName> allTypes;

  /**
   * An iterable to store the used types.
   */
  private Set<ClassName> usedTypes;

}
