package se.kth.depclean.core.analysis;

import java.util.Set;
import lombok.Data;

/**
 * POJO containing the types in an artifact.
 */
@Data
public class ArtifactTypes {

  /**
   * A HashSet to store the types.
   */
  private Set<String> allTypes;

  /**
   * A HashSet to store the used types.
   */
  private Set<String> usedTypes;

  /**
   * Ctor.
   *
   * @param allTypes  All types in the artifact.
   * @param usedTypes Thew used types in the artifact.
   */
  public ArtifactTypes(Set<String> allTypes, Set<String> usedTypes) {
    this.allTypes = allTypes;
    this.usedTypes = usedTypes;
  }
}
