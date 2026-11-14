package se.kth.depclean.core.util;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Locates the class files of a classpath entry, i.e. a jar file or a directory of classes. */
public final class ClassFiles {

  private static final Logger log = LoggerFactory.getLogger(ClassFiles.class);

  private static final String CLASS_SUFFIX = ".class";
  private static final String MULTI_RELEASE_PREFIX = "META-INF/versions/";

  /** Archives with more entries than this are not trusted (protection against ZIP bombs). */
  public static final int MAX_ENTRIES = 100_000;

  /** Entries with a longer name than this are skipped. */
  private static final int MAX_ENTRY_NAME_LENGTH = 1000;

  private ClassFiles() {}

  /**
   * Lists the classes of a classpath entry.
   *
   * @param classpathEntry a jar file or a directory of class files; anything else has no classes
   * @return the fully qualified class names, each mapped to the path of its class file relative to
   *     the root of the entry, with {@code /} as separator. The iteration order is stable.
   */
  public static Map<String, String> list(File classpathEntry) {
    if (classpathEntry.isDirectory()) {
      return listDirectory(classpathEntry.toPath());
    }
    if (classpathEntry.isFile() && classpathEntry.getName().endsWith(".jar")) {
      return listJar(classpathEntry);
    }
    return Collections.emptyMap();
  }

  /**
   * Lists the files of a classpath entry that are directly inside one of its directories.
   *
   * @param classpathEntry a jar file or a directory of class files; anything else has no files
   * @param directory the directory to list, relative to the root of the entry, with {@code /} as
   *     separator and a trailing {@code /}, e.g. {@code META-INF/services/}
   * @return the paths of the files, relative to the root of the entry, with {@code /} as separator
   */
  public static List<String> listResources(File classpathEntry, String directory) {
    final List<String> resources = new ArrayList<>();
    if (classpathEntry.isDirectory()) {
      final File[] files = new File(classpathEntry, directory).listFiles(File::isFile);
      if (files != null) {
        for (File file : files) {
          resources.add(directory + file.getName());
        }
      }
    } else if (classpathEntry.isFile() && classpathEntry.getName().endsWith(".jar")) {
      try (ZipFile zip = new ZipFile(classpathEntry)) {
        if (zip.size() > MAX_ENTRIES) {
          return resources;
        }
        final Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
          final ZipEntry entry = entries.nextElement();
          final String path = entry.getName();
          if (!entry.isDirectory()
              && path.length() <= MAX_ENTRY_NAME_LENGTH
              && path.startsWith(directory)
              && path.indexOf('/', directory.length()) < 0) {
            resources.add(path);
          }
        }
      } catch (IOException e) {
        log.warn("Unable to list the files of {}: {}", classpathEntry, e.getMessage());
      }
    }
    Collections.sort(resources);
    return resources;
  }

  private static Map<String, String> listJar(File jar) {
    final Map<String, String> classes = new LinkedHashMap<>();
    try (ZipFile zip = new ZipFile(jar)) {
      if (zip.size() > MAX_ENTRIES) {
        log.warn("{} has too many entries ({}), its classes are ignored", jar, zip.size());
        return classes;
      }
      final Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        final ZipEntry entry = entries.nextElement();
        final String path = entry.getName();
        if (!entry.isDirectory() && path.length() <= MAX_ENTRY_NAME_LENGTH) {
          addClass(path, classes);
        }
      }
    } catch (IOException e) {
      log.warn("Unable to list the classes of {}: {}", jar, e.getMessage());
    }
    return classes;
  }

  private static Map<String, String> listDirectory(Path directory) {
    final Map<String, String> classes = new LinkedHashMap<>();
    try (Stream<Path> files = Files.walk(directory)) {
      files
          .filter(Files::isRegularFile)
          .map(file -> directory.relativize(file).toString().replace(File.separatorChar, '/'))
          .sorted()
          .forEach(path -> addClass(path, classes));
    } catch (IOException | UncheckedIOException e) {
      // Files.walk reports a directory it cannot read as an UncheckedIOException
      log.warn("Unable to list the classes of {}: {}", directory, e.getMessage());
    }
    return classes;
  }

  private static void addClass(String path, Map<String, String> classes) {
    final String className = toClassName(path);
    if (className != null) {
      // The base version of a multi-release class is listed first and is the one analyzed.
      classes.putIfAbsent(className, path);
    }
  }

  /**
   * Converts the path of a class file to the name of the class.
   *
   * @param path the path of a file inside a jar or a directory of classes, with {@code /} as
   *     separator
   * @return the fully qualified class name, or null if the file is not the class file of a type
   *     ({@code module-info} and {@code package-info} are descriptors, not types)
   */
  public static String toClassName(String path) {
    if (!path.endsWith(CLASS_SUFFIX)) {
      return null;
    }
    String name = path.substring(0, path.length() - CLASS_SUFFIX.length());
    if (name.startsWith(MULTI_RELEASE_PREFIX)) {
      // META-INF/versions/<release>/com/foo/Bar
      int release = name.indexOf('/', MULTI_RELEASE_PREFIX.length());
      if (release < 0) {
        return null;
      }
      name = name.substring(release + 1);
    }
    final String simpleName = name.substring(name.lastIndexOf('/') + 1);
    if (simpleName.isEmpty() || simpleName.indexOf('-') >= 0) {
      return null;
    }
    return name.replace('/', '.');
  }
}
