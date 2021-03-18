package se.kth.depclean.core.analysis;

import java.util.Set;
import lombok.Data;

/**
 * POJO containing the types in an artifact.
 */
@Data
public class ArtifactTypes {

  /**
   * To store all types of artifacts.
   */
  private Set<String> allTypes;

  /**
   * To store used types of artifacts.
   */
  private Set<String> usedTypes;

  /**
   * To initialize the types of artifacts.
   *
   * @param allTypes  All types of artifacts.
   * @param usedTypes Used types of artifacts.
   */
  public ArtifactTypes(Set<String> allTypes, Set<String> usedTypes) {
    this.allTypes = allTypes;
    this.usedTypes = usedTypes;
  }
}
