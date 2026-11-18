package se.kth.depclean.core.analysis.model;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * How much of what an aggregator brings is used. An aggregator is a dependency without classes of
 * its own that brings other dependencies, such as a Spring Boot starter or {@code junit-jupiter}.
 * Its own bytecode cannot be used, so the analysis reports it as potentially unused; this tells
 * whether what it brings is.
 */
@SuppressWarnings("java:S6206") // records are Java 16+, this module is built with --release 8
public final class AggregatorUsage {

  private final int dependencies;
  private final int usedDependencies;

  /**
   * Creates the usage of an aggregator.
   *
   * @param dependencies the dependencies with classes that the aggregator brings, directly or
   *     through other aggregators
   * @param usedDependencies how many of them are used
   */
  public AggregatorUsage(int dependencies, int usedDependencies) {
    this.dependencies = dependencies;
    this.usedDependencies = usedDependencies;
  }

  /**
   * The dependencies with classes that the aggregator brings, in the resolved dependency tree.
   * Dependencies without classes, such as nested aggregators, are not counted.
   *
   * @return the number of dependencies
   */
  public int getDependencies() {
    return dependencies;
  }

  /**
   * How many of the dependencies the aggregator brings are used.
   *
   * @return the number of used dependencies
   */
  public int getUsedDependencies() {
    return usedDependencies;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AggregatorUsage)) {
      return false;
    }
    AggregatorUsage that = (AggregatorUsage) o;
    return dependencies == that.dependencies && usedDependencies == that.usedDependencies;
  }

  @Override
  public int hashCode() {
    return Objects.hash(dependencies, usedDependencies);
  }

  @Override
  public String toString() {
    return usedDependencies + " of " + dependencies + " dependencies it brings are used";
  }
}
