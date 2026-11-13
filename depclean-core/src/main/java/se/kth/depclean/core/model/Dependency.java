package se.kth.depclean.core.model;

import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.util.Objects;
import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import se.kth.depclean.core.util.ClassFiles;

/** Identifies a dependency to analyse. */
public class Dependency {

  @NonNull private final String groupId;
  @NonNull private final String dependencyId;
  @NonNull private final String version;
  @Nullable private final String scope;
  @Nullable private final File file;
  @NonNull private final Long size;

  @NonNull private final Iterable<ClassName> relatedClasses;

  /**
   * Creates a dependency.
   *
   * @param groupId groupId
   * @param dependencyId dependencyId
   * @param version version
   * @param scope scope
   * @param file the related dependency file (a jar in most cases)
   */
  public Dependency(
      @NonNull String groupId,
      @NonNull String dependencyId,
      @NonNull String version,
      @Nullable String scope,
      @Nullable File file) {
    this.groupId = groupId;
    this.dependencyId = dependencyId;
    this.version = version;
    this.scope = scope;
    this.file = file;
    this.relatedClasses = findRelatedClasses();
    this.size = calculateSize(file);
  }

  /**
   * Creates a dependency for the current project.
   *
   * @param groupId groupId
   * @param dependencyId dependencyId
   * @param version version
   * @param file the related dependency file (a jar in most cases)
   */
  public Dependency(
      @NonNull String groupId,
      @NonNull String dependencyId,
      @NonNull String version,
      @Nullable File file) {
    this(groupId, dependencyId, version, null, file);
  }

  @SuppressWarnings("CopyConstructorMissesField")
  protected Dependency(@NonNull Dependency dependency) {
    this(
        dependency.getGroupId(),
        dependency.getDependencyId(),
        dependency.getVersion(),
        dependency.getScope(),
        dependency.getFile());
  }

  @NonNull
  public String getGroupId() {
    return groupId;
  }

  @NonNull
  public String getDependencyId() {
    return dependencyId;
  }

  @NonNull
  public String getVersion() {
    return version;
  }

  @Nullable
  public String getScope() {
    return scope;
  }

  @Nullable
  public File getFile() {
    return file;
  }

  @NonNull
  public Long getSize() {
    return size;
  }

  @NonNull
  public Iterable<ClassName> getRelatedClasses() {
    return relatedClasses;
  }

  @Override
  @NonNull
  public String toString() {
    return groupId + ":" + dependencyId + ":" + version + ":" + scope;
  }

  @NonNull
  public String printWithSize() {
    return String.format("%s (%s)", this, FileUtils.byteCountToDisplaySize(getSize()));
  }

  @NonNull
  private Iterable<ClassName> findRelatedClasses() {
    if (file == null) {
      return ImmutableSet.of();
    }
    final ImmutableSet.Builder<ClassName> classes = ImmutableSet.builder();
    ClassFiles.list(file).keySet().forEach(className -> classes.add(new ClassName(className)));
    return classes.build();
  }

  @NonNull
  private Long calculateSize(@Nullable File file) {
    try {
      return FileUtils.sizeOf(file);
    } catch (IllegalArgumentException | NullPointerException e) {
      // File does not exist
      return 0L;
    }
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Dependency)) {
      return false;
    }
    Dependency that = (Dependency) o;
    return Objects.equals(groupId, that.groupId)
        && Objects.equals(dependencyId, that.dependencyId)
        && Objects.equals(version, that.version)
        && Objects.equals(scope, that.scope);
  }

  @Override
  public int hashCode() {
    return Objects.hash(groupId, dependencyId, version);
  }
}
