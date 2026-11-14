package se.kth.depclean.core.analysis;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
   * A service that is not looked up by the code of a project but by the launcher that runs its
   * tests. A test engine on the classpath is in use although no class references this type.
   */
  private static final String TEST_ENGINE = "org.junit.platform.engine.TestEngine";

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
    return analyze(
        projectClassDirectories,
        Collections.emptySet(),
        classpath,
        Collections.emptySet(),
        Collections.emptySet());
  }

  /**
   * Analyzes the bytecode of a project, keeping apart what the main code and what the tests use.
   *
   * <p>Besides the references of the bytecode, the analysis follows the {@link
   * java.util.ServiceLoader} mechanism: when a service type is reachable, the implementations that
   * the jars of the classpath register for it in {@code META-INF/services} are reachable too.
   *
   * @param mainClassDirectories the directories with the compiled main classes of the project;
   *     directories that do not exist are skipped
   * @param testClassDirectories the directories with the compiled test classes of the project
   * @param classpath the jars and class directories the project depends on
   * @param mainRoots classes known to be used by the main code although its bytecode does not
   *     reference them (e.g. classes named in configuration files); what they reference is used as
   *     well
   * @param testRoots the same, for the tests
   * @return the classes reachable from the project
   */
  public ClassUsageGraph analyze(
      Collection<Path> mainClassDirectories,
      Collection<Path> testClassDirectories,
      Collection<File> classpath,
      Collection<String> mainRoots,
      Collection<String> testRoots) {
    final Map<String, Set<String>> usagesPerClass = new HashMap<>();
    final Map<String, Set<String>> serviceProviders = new HashMap<>();
    final Deque<String> pending = new ArrayDeque<>();

    final Set<String> mainClasses = new LinkedHashSet<>();
    for (Path directory : mainClassDirectories) {
      analyzeProjectDirectory(directory, mainClasses, usagesPerClass, pending);
    }
    final Set<String> testClasses = new LinkedHashSet<>();
    for (Path directory : testClassDirectories) {
      analyzeProjectDirectory(directory, testClasses, usagesPerClass, pending);
    }

    final Set<String> projectClasses = new LinkedHashSet<>(mainClasses);
    projectClasses.addAll(testClasses);

    // Where the main code starts from: its own classes and the classes it is configured with.
    final Set<String> mainEntryPoints = new LinkedHashSet<>(mainClasses);
    mainEntryPoints.addAll(mainRoots);
    final Set<String> entryPoints = new LinkedHashSet<>(mainEntryPoints);
    entryPoints.addAll(testClasses);
    entryPoints.addAll(testRoots);
    if (!testClasses.isEmpty()) {
      entryPoints.add(TEST_ENGINE);
    }
    pending.addAll(entryPoints);

    try (Classpath index = new Classpath(classpath)) {
      // Project classes are analyzed already, but can be a service type themselves.
      final Set<String> visited = new HashSet<>();
      while (!pending.isEmpty()) {
        final String className = pending.pop();
        if (!visited.add(className)) {
          continue;
        }
        final Set<String> references = new HashSet<>();
        if (!projectClasses.contains(className)) {
          final Set<String> bytecodeReferences = index.referencesOf(className);
          if (bytecodeReferences != null) {
            usagesPerClass.put(className, references);
            references.addAll(bytecodeReferences);
          }
        }
        final Set<String> providers = index.providersOf(className);
        if (!providers.isEmpty()) {
          serviceProviders.put(className, providers);
          usagesPerClass.computeIfAbsent(className, k -> references).addAll(providers);
          references.addAll(providers);
        }
        for (String reference : references) {
          if (!visited.contains(reference)) {
            pending.push(reference);
          }
        }
      }
    }
    log.debug(
        "Analyzed {} project classes and {} classpath classes",
        projectClasses.size(),
        usagesPerClass.size() - projectClasses.size());
    return new ClassUsageGraph(
        projectClasses, mainEntryPoints, entryPoints, usagesPerClass, serviceProviders);
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

    private static final String SERVICES_DIRECTORY = "META-INF/services/";

    private final Map<String, List<Location>> locations = new HashMap<>();
    private final Map<String, List<Location>> services = new HashMap<>();
    private final Map<File, ZipFile> openJars = new HashMap<>();

    Classpath(Collection<File> entries) {
      // A set, because the same file can be on the classpath more than once
      for (File entry : new LinkedHashSet<>(entries)) {
        for (Map.Entry<String, String> classFile : ClassFiles.list(entry).entrySet()) {
          locations
              .computeIfAbsent(classFile.getKey(), k -> new ArrayList<>(1))
              .add(new Location(entry, classFile.getValue()));
        }
        for (String path : ClassFiles.listResources(entry, SERVICES_DIRECTORY)) {
          services
              .computeIfAbsent(path.substring(SERVICES_DIRECTORY.length()), k -> new ArrayList<>(1))
              .add(new Location(entry, path));
        }
      }
    }

    /**
     * The implementations that the classpath registers for a service type, in the provider
     * configuration files of {@link java.util.ServiceLoader}.
     *
     * @return the fully qualified names of the implementation classes, empty if there are none
     */
    Set<String> providersOf(String serviceType) {
      final List<Location> configurations = services.get(serviceType);
      if (configurations == null) {
        return Collections.emptySet();
      }
      final Set<String> providers = new LinkedHashSet<>();
      for (Location configuration : configurations) {
        try {
          final byte[] content = read(configuration);
          if (content != null) {
            for (String line : new String(content, StandardCharsets.UTF_8).split("\\R")) {
              final int comment = line.indexOf('#');
              final String provider = (comment < 0 ? line : line.substring(0, comment)).trim();
              if (!provider.isEmpty()) {
                providers.add(provider);
              }
            }
          }
        } catch (IOException e) {
          log.warn(
              "Unable to read {} in {}: {}",
              configuration.path,
              configuration.container,
              e.getMessage());
        }
      }
      return providers;
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
