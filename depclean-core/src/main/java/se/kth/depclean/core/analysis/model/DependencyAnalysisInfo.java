package se.kth.depclean.core.analysis.model;

import java.util.Objects;
import java.util.SortedSet;
import org.jspecify.annotations.Nullable;

/** The result of a dependency analysis. */
@SuppressWarnings("java:S6206") // records are Java 16+, this module is built with --release 8
public final class DependencyAnalysisInfo {

  private final String status;
  private final String type;
  private final Long size;
  private final SortedSet<String> allTypes;
  private final SortedSet<String> usedTypes;
  @Nullable private final DependencyUsage usage;
  @Nullable private final AggregatorUsage aggregator;
  private final boolean usedOnlyByTests;

  /** Creates the analysis info for a dependency. */
  public DependencyAnalysisInfo(
      String status,
      String type,
      Long size,
      SortedSet<String> allTypes,
      SortedSet<String> usedTypes) {
    this(status, type, size, allTypes, usedTypes, null, null, false);
  }

  /**
   * Creates the analysis info for a dependency.
   *
   * @param status used, bloated or unknown
   * @param type direct, transitive, inherited or unknown
   * @param size the size of the dependency file, in bytes
   * @param allTypes the classes of the dependency
   * @param usedTypes the classes of the dependency that the project uses
   * @param usage why the dependency is used, null if it is not
   * @param aggregator how much of what the dependency brings is used, null if it is not an
   *     aggregator
   * @param usedOnlyByTests whether the dependency is declared with a packaged scope although only
   *     the tests use it
   */
  @SuppressWarnings("java:S107") // the parameters mirror the reported facts one-to-one
  public DependencyAnalysisInfo(
      String status,
      String type,
      Long size,
      SortedSet<String> allTypes,
      SortedSet<String> usedTypes,
      @Nullable DependencyUsage usage,
      @Nullable AggregatorUsage aggregator,
      boolean usedOnlyByTests) {
    this.status = status;
    this.type = type;
    this.size = size;
    this.allTypes = allTypes;
    this.usedTypes = usedTypes;
    this.usage = usage;
    this.aggregator = aggregator;
    this.usedOnlyByTests = usedOnlyByTests;
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

  /** Why the dependency is used, null if it is not used. */
  @Nullable
  public DependencyUsage usage() {
    return usage;
  }

  /** How much of what the dependency brings is used, null if it is not an aggregator. */
  @Nullable
  public AggregatorUsage aggregator() {
    return aggregator;
  }

  /** Whether the dependency is declared with a packaged scope although only the tests use it. */
  public boolean usedOnlyByTests() {
    return usedOnlyByTests;
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
        && Objects.equals(usedTypes, that.usedTypes)
        && Objects.equals(usage, that.usage)
        && Objects.equals(aggregator, that.aggregator)
        && usedOnlyByTests == that.usedOnlyByTests;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        status, type, size, allTypes, usedTypes, usage, aggregator, usedOnlyByTests);
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
        + ", usage="
        + usage
        + ", aggregator="
        + aggregator
        + ", usedOnlyByTests="
        + usedOnlyByTests
        + "]";
  }
}
