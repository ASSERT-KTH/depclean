package se.kth.depclean.core.analysis;

import java.util.Set;
import lombok.Data;

/**
 * POJO containing the types in an artifact.
 */
@Data
public class ArtifactTypes {

  private Set<String> allTypes;
  private Set<String> usedTypes;

  public ArtifactTypes(Set<String> allTypes, Set<String> usedTypes) {
    this.allTypes = allTypes;
    this.usedTypes = usedTypes;
  }
}
