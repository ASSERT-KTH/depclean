package se.kth.depclean.core.analysis.model;

import java.util.Objects;
import java.util.SortedSet;
import org.jspecify.annotations.Nullable;

/** The result of a dependency analysis. */
public final class DependencyAnalysisInfo {

  private final String status;
  private final String type;
  private final Long size;
  private final SortedSet<String> allTypes;
  private final SortedSet<String> usedTypes;

  /** Creates the analysis info for a dependency. */
  public DependencyAnalysisInfo(
      String status,
      String type,
      Long size,
      SortedSet<String> allTypes,
      SortedSet<String> usedTypes) {
    this.status = status;
    this.type = type;
    this.size = size;
    this.allTypes = allTypes;
    this.usedTypes = usedTypes;
  }

  public String status() {
    return status;
  }

  public String type() {
    return type;
  }

  public Long size() {
    return size;
  }

  public SortedSet<String> allTypes() {
    return allTypes;
  }

  public SortedSet<String> usedTypes() {
    return usedTypes;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DependencyAnalysisInfo)) {
      return false;
    }
    DependencyAnalysisInfo that = (DependencyAnalysisInfo) o;
    return Objects.equals(status, that.status)
        && Objects.equals(type, that.type)
        && Objects.equals(size, that.size)
        && Objects.equals(allTypes, that.allTypes)
        && Objects.equals(usedTypes, that.usedTypes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, type, size, allTypes, usedTypes);
  }

  @Override
  public String toString() {
    return "DependencyAnalysisInfo[status="
        + status
        + ", type="
        + type
        + ", size="
        + size
        + ", allTypes="
        + allTypes
        + ", usedTypes="
        + usedTypes
        + "]";
  }
}
