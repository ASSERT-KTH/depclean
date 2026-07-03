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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import se.kth.depclean.core.analysis.ClassAnalyzer;
import se.kth.depclean.core.analysis.DefaultClassAnalyzer;

/** Identifies a dependency to analyse. */
@Slf4j
@Getter
public class Dependency {

  private static final int MAX_JAR_ENTRIES = 100_000;
  private static final int MAX_ENTRY_NAME_LENGTH = 1000;

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
    final Set<ClassName> discoveredClasses = new HashSet<>();
    collectRelatedClasses(discoveredClasses);
    logRelatedClasses(discoveredClasses);
    return ImmutableSet.copyOf(discoveredClasses);
  }

  private void collectRelatedClasses(Set<ClassName> discoveredClasses) {
    if (file == null) {
      return;
    }

    try {
      if (isJarFile(file)) {
        collectJarClasses(discoveredClasses);
      } else if (file.isDirectory()) {
        collectDirectoryClasses(discoveredClasses);
      }
    } catch (IOException e) {
      log.error(e.getMessage(), e);
    }
  }

  private boolean isJarFile(File candidateFile) {
    return candidateFile.getName().endsWith(".jar");
  }

  private void collectJarClasses(Set<ClassName> discoveredClasses) throws IOException {
    try (JarFile jarFile = new JarFile(file)) {
      int entryCount = collectJarEntries(jarFile, discoveredClasses);
      logTruncatedJar(entryCount);
    }
  }

  private int collectJarEntries(JarFile jarFile, Set<ClassName> discoveredClasses) {
    Enumeration<JarEntry> jarEntries = jarFile.entries();
    int entryCount = 0;

    while (jarEntries.hasMoreElements() && entryCount < MAX_JAR_ENTRIES) {
      JarEntry jarEntry = jarEntries.nextElement();
      entryCount++;
      addClassEntry(jarEntry.getName(), discoveredClasses);
    }

    return entryCount;
  }

  private void addClassEntry(String entryName, Set<ClassName> discoveredClasses) {
    if (isSuspiciousEntry(entryName) || !entryName.endsWith(".class")) {
      return;
    }

    discoveredClasses.add(new ClassName(entryName));
  }

  private boolean isSuspiciousEntry(String entryName) {
    return entryName.length() > MAX_ENTRY_NAME_LENGTH;
  }

  private void logTruncatedJar(int entryCount) {
    if (entryCount < MAX_JAR_ENTRIES) {
      return;
    }

    log.warn(
        "JAR file {} has too many entries ({}), processing truncated", file.getName(), entryCount);
  }

  private void collectDirectoryClasses(Set<ClassName> discoveredClasses) throws IOException {
    URL url = file.toURI().toURL();
    ClassAnalyzer classAnalyzer = new DefaultClassAnalyzer();
    Set<String> classes = classAnalyzer.analyze(url);
    classes.forEach(c -> discoveredClasses.add(new ClassName(c)));
  }

  private void logRelatedClasses(Set<ClassName> discoveredClasses) {
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
    log.trace("Related classes: " + discoveredClasses);
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
    if (!(o instanceof Dependency that)) {
      return false;
    }
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
