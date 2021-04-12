package se.kth.depclean.core.analysis;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * POJO containing the types in an artifact.
 */
@Data
@AllArgsConstructor
public class ArtifactTypes {

  /**
   * A HashSet to store the types.
   */
  private Set<String> allTypes;

  /**
   * A HashSet to store the used types.
   */
  private Set<String> usedTypes;

}
