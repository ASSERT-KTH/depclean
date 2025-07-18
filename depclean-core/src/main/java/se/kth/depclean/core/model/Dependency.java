package se.kth.depclean.core.model;

import static com.google.common.collect.ImmutableSet.copyOf;

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
import se.kth.depclean.core.analysis.ClassAnalyzer;
import se.kth.depclean.core.analysis.DefaultClassAnalyzer;

/**
 * Identifies a dependency to analyse.
 */
@Slf4j
@Getter
public class Dependency {

  private final String groupId;
  private final String dependencyId;
  private final String version;
  private final String scope;
  private final File file;
  private final Long size;

  private final Iterable<ClassName> relatedClasses;

  /**
   * Creates a dependency.
   *
   * @param groupId      groupId
   * @param dependencyId dependencyId
   * @param version      version
   * @param scope        scope
   * @param file         the related dependency file (a jar in most cases)
   */
  public Dependency(String groupId, String dependencyId, String version, String scope, File file) {
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
   * @param groupId      groupId
   * @param dependencyId dependencyId
   * @param version      version
   * @param file         the related dependency file (a jar in most cases)
   */
  public Dependency(String groupId, String dependencyId, String version, File file) {
    this(groupId, dependencyId, version, null, file);
  }

  @SuppressWarnings("CopyConstructorMissesField")
  protected Dependency(Dependency dependency) {
    this(dependency.getGroupId(), dependency.getDependencyId(), dependency.getVersion(),
        dependency.getScope(), dependency.getFile());
  }

  @Override
  public String toString() {
    return String.format("%s:%s:%s:%s", groupId, dependencyId, version, scope);
  }

  public String printWithSize() {
    return String.format("%s (%s)", this, FileUtils.byteCountToDisplaySize(getSize()));
  }

  private Iterable<ClassName> findRelatedClasses() {
    final Set<ClassName> relatedClasses = new HashSet<>();
    if (file != null && file.getName().endsWith(".jar")) {
      // optimized solution for the jar case
      try (JarFile jarFile = new JarFile(file)) {
        Enumeration<JarEntry> jarEntries = jarFile.entries();
        while (jarEntries.hasMoreElements()) {
          String entry = jarEntries.nextElement().getName();
          if (entry.endsWith(".class")) {
            relatedClasses.add(new ClassName(entry));
          }
        }
      } catch (IOException e) {
        log.error(e.getMessage(), e);
      }
    } else if (file != null && file.isDirectory()) {
      try {
        URL url = file.toURI().toURL();
        ClassAnalyzer classAnalyzer = new DefaultClassAnalyzer();
        Set<String> classes = classAnalyzer.analyze(url);
        classes.forEach(c -> relatedClasses.add(new ClassName(c)));
      } catch (IOException e) {
        log.error(e.getMessage(), e);
      }
    }
    log.trace("Finding related classes for Dependency: " + groupId + ":" + dependencyId + ":" + version + ":" + scope
        + ":" + file);
    log.trace("Related classes: " + relatedClasses);
    return copyOf(relatedClasses);
  }

  private Long calculateSize(File file) {
    try {
      return FileUtils.sizeOf(file);
    } catch (IllegalArgumentException | NullPointerException e) {
      // File does not exist
      return 0L;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
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
