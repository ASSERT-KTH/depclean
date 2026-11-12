package se.kth.depclean.core.analysis;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.depclean.core.analysis.asm.ClassReferenceExtractor;
import se.kth.depclean.core.analysis.asm.ClassReferenceExtractor.Depth;
import se.kth.depclean.core.analysis.graph.ClassUsageGraph;
import se.kth.depclean.core.util.ClassFiles;

/**
 * Finds the classes that a project uses on its classpath.
 *
 * <p>The analysis starts from the compiled classes of the project and follows the references of the
 * bytecode. The class files of the dependencies are read straight from their jars, and only on
 * demand: a class that the project cannot reach is never opened. On a typical project that is the
 * vast majority of the classpath.
 */
public final class ClassUsageAnalyzer {

  private static final Logger log = LoggerFactory.getLogger(ClassUsageAnalyzer.class);

  /** A class file bigger than this is not a real class file (protection against ZIP bombs). */
  private static final long MAX_CLASS_FILE_SIZE = 64L * 1024 * 1024;

  /**
   * Analyzes the bytecode of a project.
   *
   * @param projectClassDirectories the directories with the compiled classes of the project;
   *     directories that do not exist are skipped
   * @param classpath the jars and class directories the project depends on
   * @return the classes reachable from the project
   */
  public ClassUsageGraph analyze(
      Collection<Path> projectClassDirectories, Collection<File> classpath) {
    return analyze(projectClassDirectories, classpath, Collections.emptySet());
  }

  /**
   * Analyzes the bytecode of a project, with additional starting points.
   *
   * @param projectClassDirectories the directories with the compiled classes of the project;
   *     directories that do not exist are skipped
   * @param classpath the jars and class directories the project depends on
   * @param extraRoots classes known to be used although the bytecode of the project does not
   *     reference them (e.g. classes named in configuration files); what they reference is used as
   *     well
   * @return the classes reachable from the project
   */
  public ClassUsageGraph analyze(
      Collection<Path> projectClassDirectories,
      Collection<File> classpath,
      Collection<String> extraRoots) {
    final Map<String, Set<String>> usagesPerClass = new HashMap<>();
    final Set<String> projectClasses = new LinkedHashSet<>();
    final Deque<String> pending = new ArrayDeque<>(extraRoots);

    for (Path directory : projectClassDirectories) {
      analyzeProjectDirectory(directory, projectClasses, usagesPerClass, pending);
    }

    try (Classpath index = new Classpath(classpath)) {
      final Set<String> visited = new HashSet<>(projectClasses);
      while (!pending.isEmpty()) {
        final String className = pending.pop();
        if (!visited.add(className)) {
          continue;
        }
        final Set<String> references = index.referencesOf(className);
        if (references != null) {
          usagesPerClass.put(className, references);
          for (String reference : references) {
            if (!visited.contains(reference)) {
              pending.push(reference);
            }
          }
        }
      }
    }
    log.debug(
        "Analyzed {} project classes and {} classpath classes",
        projectClasses.size(),
        usagesPerClass.size() - projectClasses.size());
    return new ClassUsageGraph(projectClasses, usagesPerClass);
  }

  private static void analyzeProjectDirectory(
      Path directory,
      Set<String> projectClasses,
      Map<String, Set<String>> usagesPerClass,
      Deque<String> pending) {
    if (!Files.isDirectory(directory)) {
      return;
    }
    for (Map.Entry<String, String> classFile : ClassFiles.list(directory.toFile()).entrySet()) {
      final String className = classFile.getKey();
      final Path file = directory.resolve(classFile.getValue());
      try {
        final Set<String> references =
            ClassReferenceExtractor.extract(Files.readAllBytes(file), Depth.SOURCE);
        projectClasses.add(className);
        // The same class can be compiled in several directories (e.g. Gradle source sets)
        usagesPerClass.computeIfAbsent(className, k -> new HashSet<>()).addAll(references);
        pending.addAll(references);
      } catch (IOException | IllegalArgumentException e) {
        log.warn("Unable to analyze the class file {}: {}", file, e.getMessage());
      }
    }
  }

  /** An index of the classes of the classpath, which reads class files on demand. */
  private static final class Classpath implements Closeable {

    private final Map<String, List<Location>> locations = new HashMap<>();
    private final Map<File, ZipFile> openJars = new HashMap<>();

    Classpath(Collection<File> entries) {
      // A set, because the same file can be on the classpath more than once
      for (File entry : new LinkedHashSet<>(entries)) {
        for (Map.Entry<String, String> classFile : ClassFiles.list(entry).entrySet()) {
          locations
              .computeIfAbsent(classFile.getKey(), k -> new ArrayList<>(1))
              .add(new Location(entry, classFile.getValue()));
        }
      }
    }

    /**
     * The classes referenced by a class of the classpath. When several classpath entries contain
     * the class, the references of all its versions are returned, because which version wins at
     * runtime is not known here.
     *
     * @return null if the class is not on the classpath
     */
    @Nullable Set<String> referencesOf(String className) {
      final List<Location> classLocations = locations.get(className);
      if (classLocations == null) {
        return null;
      }
      final Set<String> references = new HashSet<>();
      for (Location location : classLocations) {
        try {
          final byte[] classFile = read(location);
          if (classFile != null) {
            references.addAll(ClassReferenceExtractor.extract(classFile, Depth.LINKAGE));
          }
        } catch (IOException | IllegalArgumentException e) {
          log.warn(
              "Unable to analyze {} in {}: {}", location.path, location.container, e.getMessage());
        }
      }
      return references;
    }

    private byte @Nullable [] read(Location location) throws IOException {
      if (location.container.isDirectory()) {
        return Files.readAllBytes(location.container.toPath().resolve(location.path));
      }
      ZipFile jar = openJars.get(location.container);
      if (jar == null) {
        jar = new ZipFile(location.container);
        openJars.put(location.container, jar);
      }
      final ZipEntry entry = jar.getEntry(location.path);
      if (entry == null || entry.getSize() > MAX_CLASS_FILE_SIZE) {
        return null;
      }
      try (InputStream in =
          new BoundedInputStream(jar.getInputStream(entry), MAX_CLASS_FILE_SIZE)) {
        return IOUtils.toByteArray(in);
      }
    }

    @Override
    public void close() {
      for (ZipFile jar : openJars.values()) {
        try {
          jar.close();
        } catch (IOException e) {
          log.debug("Unable to close {}: {}", jar.getName(), e.getMessage());
        }
      }
      openJars.clear();
    }
  }

  /** Where the class file of a class is: a path inside a jar or a directory. */
  private static final class Location {
    private final File container;
    private final String path;

    Location(File container, String path) {
      this.container = container;
      this.path = path;
    }
  }
}
