package se.kth.depclean.core.analysis;

import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import se.kth.depclean.core.model.ClassName;

/** POJO containing the types in a dependency. */
public class DependencyTypes {

  /** An iterable to store the types. */
  private Set<ClassName> allTypes;

  /** An iterable to store the used types. */
  private Set<ClassName> usedTypes;

  public DependencyTypes(Set<ClassName> allTypes, Set<ClassName> usedTypes) {
    this.allTypes = allTypes;
    this.usedTypes = usedTypes;
  }

  public Set<ClassName> getAllTypes() {
    return allTypes;
  }

  public Set<ClassName> getUsedTypes() {
    return usedTypes;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DependencyTypes that = (DependencyTypes) o;
    return Objects.equals(allTypes, that.allTypes) && Objects.equals(usedTypes, that.usedTypes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(allTypes, usedTypes);
  }
}
