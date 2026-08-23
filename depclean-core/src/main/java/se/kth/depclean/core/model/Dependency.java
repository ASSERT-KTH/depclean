package se.kth.depclean.core.model;

import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.depclean.core.analysis.ClassAnalyzer;
import se.kth.depclean.core.analysis.DefaultClassAnalyzer;

/** Identifies a dependency to analyse. */
public class Dependency {

  private static final Logger log = LoggerFactory.getLogger(Dependency.class);

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
    return String.format("%s:%s:%s:%s", groupId, dependencyId, version, scope);
  }

  @NonNull
  public String printWithSize() {
    return String.format("%s (%s)", this, FileUtils.byteCountToDisplaySize(getSize()));
  }

  @NonNull
  private Iterable<ClassName> findRelatedClasses() {
    final Set<ClassName> classes = new HashSet<>();
    if (file != null && file.getName().endsWith(".jar")) {
      // optimized solution for the jar case
      addClassesFromJar(file, classes);
    } else if (file != null && file.isDirectory()) {
      addClassesFromDirectory(file, classes);
    }
    log.trace(
        "Finding related classes for Dependency: "
            + groupId
            + ":"
            + dependencyId
            + ":"
            + version
            + ":"
            + scope
            + ":"
            + file);
    log.trace("Related classes: " + classes);
    return ImmutableSet.copyOf(classes);
  }

  private void addClassesFromJar(File jar, Set<ClassName> classes) {
    try (JarFile jarFile = new JarFile(jar)) {
      Enumeration<JarEntry> jarEntries = jarFile.entries();

      // Protection against ZIP bomb attacks
      int maxEntries = 100_000; // Maximum number of entries to process
      int entryCount = 0;

      while (jarEntries.hasMoreElements() && entryCount < maxEntries) {
        JarEntry jarEntry = jarEntries.nextElement();
        String entry = jarEntry.getName();
        entryCount++;

        // Additional protection: skip entries with suspicious characteristics
        if (entry.length() > 1000) { // Skip entries with very long names
          continue;
        }

        if (entry.endsWith(".class")) {
          classes.add(new ClassName(entry));
        }
      }

      if (entryCount >= maxEntries) {
        log.warn(
            "JAR file {} has too many entries ({}), processing truncated",
            jar.getName(),
            entryCount);
      }
    } catch (IOException e) {
      log.error(e.getMessage(), e);
    }
  }

  private void addClassesFromDirectory(File directory, Set<ClassName> classes) {
    try {
      URL url = directory.toURI().toURL();
      ClassAnalyzer classAnalyzer = new DefaultClassAnalyzer();
      Set<String> analyzedClasses = classAnalyzer.analyze(url);
      analyzedClasses.forEach(c -> classes.add(new ClassName(c)));
    } catch (IOException e) {
      log.error(e.getMessage(), e);
    }
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
