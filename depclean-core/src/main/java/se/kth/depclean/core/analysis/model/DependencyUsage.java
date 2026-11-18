package se.kth.depclean.core.analysis.model;

import java.util.Collections;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * Why the analysis considers a dependency used: the evidence it found for it. A dependency can be
 * used for several reasons at once.
 */
@SuppressWarnings("java:S6206") // records are Java 16+, this module is built with --release 8
public final class DependencyUsage {

  private final SortedSet<String> mainClasses;
  private final SortedSet<String> testClasses;
  private final SortedSet<String> dependencies;
  private final SortedSet<String> services;
  private final SortedSet<String> xmlResourceClasses;
  private final SortedSet<String> importedClasses;
  private final SortedSet<String> annotationProcessors;
  private final boolean ignored;

  private DependencyUsage(Builder builder) {
    this.mainClasses = Collections.unmodifiableSortedSet(new TreeSet<>(builder.mainClasses));
    this.testClasses = Collections.unmodifiableSortedSet(new TreeSet<>(builder.testClasses));
    this.dependencies = Collections.unmodifiableSortedSet(new TreeSet<>(builder.dependencies));
    this.services = Collections.unmodifiableSortedSet(new TreeSet<>(builder.services));
    this.xmlResourceClasses =
        Collections.unmodifiableSortedSet(new TreeSet<>(builder.xmlResourceClasses));
    this.importedClasses =
        Collections.unmodifiableSortedSet(new TreeSet<>(builder.importedClasses));
    this.annotationProcessors =
        Collections.unmodifiableSortedSet(new TreeSet<>(builder.annotationProcessors));
    this.ignored = builder.ignored;
  }

  /**
   * Starts an empty usage.
   *
   * @return a builder without any evidence
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * The classes of the main code of the project whose bytecode references a class of the
   * dependency.
   *
   * @return fully qualified class names
   */
  public SortedSet<String> getMainClasses() {
    return mainClasses;
  }

  /**
   * The test classes of the project whose bytecode references a class of the dependency.
   *
   * @return fully qualified class names
   */
  public SortedSet<String> getTestClasses() {
    return testClasses;
  }

  /**
   * The other dependencies with a class, reachable from the project, that references a class of the
   * dependency. They are how the project reaches the dependency without referencing it itself.
   *
   * @return {@code groupId:artifactId:version} coordinates
   */
  public SortedSet<String> getDependencies() {
    return dependencies;
  }

  /**
   * The service types, reachable from the project, for which the dependency registers an
   * implementation in {@code META-INF/services}.
   *
   * @return fully qualified names of the service types
   */
  public SortedSet<String> getServices() {
    return services;
  }

  /**
   * The classes of the dependency named in an XML resource of the project.
   *
   * @return fully qualified class names
   */
  public SortedSet<String> getXmlResourceClasses() {
    return xmlResourceClasses;
  }

  /**
   * The classes of the dependency imported in the sources of the project.
   *
   * @return fully qualified class names
   */
  public SortedSet<String> getImportedClasses() {
    return importedClasses;
  }

  /**
   * The annotation processors of the dependency that the build is configured with.
   *
   * @return fully qualified class names
   */
  public SortedSet<String> getAnnotationProcessors() {
    return annotationProcessors;
  }

  /**
   * Whether the user asked to consider the dependency used, by its coordinates or its scope. It can
   * be the only reason the dependency is used.
   *
   * @return true if the dependency is ignored by the analysis
   */
  public boolean isIgnored() {
    return ignored;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DependencyUsage)) {
      return false;
    }
    DependencyUsage that = (DependencyUsage) o;
    return ignored == that.ignored
        && mainClasses.equals(that.mainClasses)
        && testClasses.equals(that.testClasses)
        && dependencies.equals(that.dependencies)
        && services.equals(that.services)
        && xmlResourceClasses.equals(that.xmlResourceClasses)
        && importedClasses.equals(that.importedClasses)
        && annotationProcessors.equals(that.annotationProcessors);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        mainClasses,
        testClasses,
        dependencies,
        services,
        xmlResourceClasses,
        importedClasses,
        annotationProcessors,
        ignored);
  }

  @Override
  public String toString() {
    return "DependencyUsage[mainClasses="
        + mainClasses
        + ", testClasses="
        + testClasses
        + ", dependencies="
        + dependencies
        + ", services="
        + services
        + ", xmlResourceClasses="
        + xmlResourceClasses
        + ", importedClasses="
        + importedClasses
        + ", annotationProcessors="
        + annotationProcessors
        + ", ignored="
        + ignored
        + "]";
  }

  /** Collects the evidence that a dependency is used. */
  public static final class Builder {

    private final SortedSet<String> mainClasses = new TreeSet<>();
    private final SortedSet<String> testClasses = new TreeSet<>();
    private final SortedSet<String> dependencies = new TreeSet<>();
    private final SortedSet<String> services = new TreeSet<>();
    private final SortedSet<String> xmlResourceClasses = new TreeSet<>();
    private final SortedSet<String> importedClasses = new TreeSet<>();
    private final SortedSet<String> annotationProcessors = new TreeSet<>();
    private boolean ignored;

    private Builder() {}

    /** Records a class of the main code that references the dependency. */
    public Builder mainClass(String className) {
      mainClasses.add(className);
      return this;
    }

    /** Records a test class that references the dependency. */
    public Builder testClass(String className) {
      testClasses.add(className);
      return this;
    }

    /** Records another dependency, by its coordinates, that references the dependency. */
    public Builder dependency(String coordinates) {
      dependencies.add(coordinates);
      return this;
    }

    /** Records a reachable service type the dependency provides an implementation of. */
    public Builder service(String serviceType) {
      services.add(serviceType);
      return this;
    }

    /** Records a class of the dependency named in an XML resource. */
    public Builder xmlResourceClass(String className) {
      xmlResourceClasses.add(className);
      return this;
    }

    /** Records a class of the dependency imported in the sources. */
    public Builder importedClass(String className) {
      importedClasses.add(className);
      return this;
    }

    /** Records an annotation processor of the dependency that the build uses. */
    public Builder annotationProcessor(String className) {
      annotationProcessors.add(className);
      return this;
    }

    /** Records that the user asked to consider the dependency used. */
    public Builder ignored() {
      ignored = true;
      return this;
    }

    public DependencyUsage build() {
      return new DependencyUsage(this);
    }
  }
}
