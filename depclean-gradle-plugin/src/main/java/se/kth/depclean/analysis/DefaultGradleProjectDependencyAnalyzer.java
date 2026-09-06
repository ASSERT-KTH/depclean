package se.kth.depclean.analysis;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.depclean.core.analysis.ClassAnalyzer;
import se.kth.depclean.core.analysis.DefaultClassAnalyzer;
import se.kth.depclean.core.analysis.DependencyAnalyzer;
import se.kth.depclean.core.analysis.DependencyTypes;
import se.kth.depclean.core.analysis.asm.AsmDependencyAnalyzer;
import se.kth.depclean.core.analysis.graph.DefaultCallGraph;
import se.kth.depclean.core.analysis.src.XmlClassesAnalyzer;
import se.kth.depclean.core.model.ClassName;
import se.kth.depclean.utils.ClassesDirectoryFinder;
import se.kth.depclean.utils.DependencyUtils;

/** This is principal class that perform the dependency analysis in a Gradle project. */
public class DefaultGradleProjectDependencyAnalyzer implements GradleProjectDependencyAnalyzer {

  private static final Logger log =
      LoggerFactory.getLogger(DefaultGradleProjectDependencyAnalyzer.class);

  private final ClassAnalyzer classAnalyzer = new DefaultClassAnalyzer();

  private final DependencyAnalyzer dependencyAnalyzer = new AsmDependencyAnalyzer();

  /** If true, the project's classes in target/test-classes are not going to be analyzed. */
  private final boolean isIgnoredTest;

  /** A map [artifact] -> [allTypes]. */
  private Map<ResolvedArtifact, Set<String>> artifactClassesMap = new HashMap<>();

  /** A map [artifact] -> [usedTypes]. */
  private final Map<ResolvedArtifact, Set<String>> artifactUsedClassesMap = new HashMap<>();

  /** Lazily computed from the two maps above, see {@link #getDependenciesClassesMap()}. */
  @Nullable private Map<String, DependencyTypes> dependenciesClassesMap;

  /** Ctor. */
  public DefaultGradleProjectDependencyAnalyzer(final boolean isIgnoredTest) {
    this.isIgnoredTest = isIgnoredTest;
  }

  /**
   * Analyze the dependencies in a project.
   *
   * @param project The Gradle project to be analyzed.
   * @return An object with the usedDeclaredArtifacts, usedUndeclaredArtifacts, and
   *     unusedDeclaredArtifacts.
   * @see <code>ProjectDependencyAnalyzer#analyze(org.apache.invoke.project.MavenProject)</code>
   */
  @Override
  public GradleProjectDependencyAnalysis analyze(final Project project) {
    try {
      // Use the filtered resolvable configurations instead of all configurations
      DependencyUtils utils = new DependencyUtils();
      Set<Configuration> configurations = utils.getResolvableConfigurations(project);

      // all resolved dependencies including transitive ones of the project.
      Set<ResolvedDependency> allDependencies = utils.getAllDependencies(configurations);

      // all resolved artifacts of this project
      Set<ResolvedArtifact> allArtifacts = new HashSet<>();
      for (ResolvedDependency dependency : allDependencies) {
        allArtifacts.addAll(dependency.getModuleArtifacts());
      }

      // a map of [dependency] -> [classes]
      artifactClassesMap = buildArtifactClassMap(allArtifacts);
      dependenciesClassesMap = null;

      // direct dependencies of the project
      Set<ResolvedDependency> declaredDependencies = utils.getDeclaredDependencies(configurations);

      // direct artifacts of the project
      Set<ResolvedArtifact> declaredArtifacts = utils.getDeclaredArtifacts(declaredDependencies);

      /* ******************** bytecode analysis ********************* */

      // The call graph is kept in static fields, and in a multi-project build the same task
      // action analyzes every project, so the graph of a previously analyzed project must be
      // discarded before building the graph of this one.
      DefaultCallGraph.clear();

      // execute the analysis (note that the order of these operations matters!)
      buildProjectDependencyClasses(project);
      Set<String> projectClasses = new HashSet<>(DefaultCallGraph.getProjectVertices());
      buildDependenciesDependencyClasses(project);

      /* ******************** usage analysis ********************* */

      // classes referenced in the bytecode, plus classes referenced in XML resources
      // (e.g. Spring XML configurations, web.xml), see issues #78 and #81
      Set<String> referencedClasses =
          new HashSet<>(DefaultCallGraph.referencedClassMembers(projectClasses));
      referencedClasses.addAll(collectClassesFromXmlResources(project));

      // search for the dependencies used by the project
      Set<ResolvedArtifact> usedArtifacts =
          collectUsedArtifacts(artifactClassesMap, referencedClasses);

      /*
       * ******************** results as statically used at the bytecode
       * ***********************
       */

      // for the used dependencies, get the ones that are declared
      Set<ResolvedArtifact> usedDeclaredArtifacts = new LinkedHashSet<>(declaredArtifacts);
      usedDeclaredArtifacts.retainAll(usedArtifacts);

      // for the used dependencies, remove the ones that are declared
      Set<ResolvedArtifact> usedUndeclaredArtifacts = new LinkedHashSet<>(usedArtifacts);
      usedUndeclaredArtifacts = removeAll(usedUndeclaredArtifacts, declaredArtifacts);

      // for the declared dependencies, get the ones that are not used
      Set<ResolvedArtifact> unusedDeclaredArtifacts = new LinkedHashSet<>(declaredArtifacts);
      unusedDeclaredArtifacts = removeAll(unusedDeclaredArtifacts, usedArtifacts);

      return new GradleProjectDependencyAnalysis(
          usedDeclaredArtifacts, usedUndeclaredArtifacts, unusedDeclaredArtifacts);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns a map with the artifacts (dependencies) in a Gradle project and their corresponding
   * classes.
   *
   * @param allArtifacts File of each artifact.
   * @return A map of artifact -> classes.
   * @throws IOException If the class cannot be analyzed.
   */
  public Map<ResolvedArtifact, Set<String>> buildArtifactClassMap(
      final Set<ResolvedArtifact> allArtifacts) throws IOException {
    Map<ResolvedArtifact, Set<String>> artifactClassMap = new LinkedHashMap<>();
    for (ResolvedArtifact artifact : allArtifacts) {
      File file = artifact.getFile();
      if (file.getName().endsWith(".jar")) {
        // optimized solution for the jar case
        artifactClassMap.put(artifact, classesFromJar(file));
      } else if (file.isDirectory()) {
        URL url = file.toURI().toURL();
        Set<String> classes = classAnalyzer.analyze(url);
        artifactClassMap.put(artifact, classes);
      }
    }
    return artifactClassMap;
  }

  /** Collects the class names contained in a jar file, enforcing ZIP bomb limits. */
  private Set<String> classesFromJar(File file) throws IOException {
    try (JarFile jarFile = new JarFile(file)) {
      Enumeration<JarEntry> jarEntries = jarFile.entries();
      Set<String> classes = new HashSet<>();

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
          String className = entry.replace('/', '.');
          className = className.substring(0, className.length() - ".class".length());
          classes.add(className);
        }
      }

      if (entryCount >= maxEntries) {
        log.warn(
            "JAR file {} has too many entries ({}), processing truncated",
            file.getName(),
            entryCount);
      }

      return classes;
    }
  }

  /**
   * Get the project's build classes.
   *
   * @param project The gradle project.
   * @throws IOException In case of IO issues.
   */
  private void buildProjectDependencyClasses(final Project project) throws IOException {
    // Analyze classes from all source sets and layouts (JVM, custom, Android variants)
    for (File classesDir : ClassesDirectoryFinder.findClassesDirectories(project, isIgnoredTest)) {
      checkThenCollectDependencyClasses(classesDir);
    }
  }

  /**
   * Get the project's build dependency classes.
   *
   * @param project The gradle project.
   * @throws IOException In case of IO issues.
   */
  private void buildDependenciesDependencyClasses(final Project project) throws IOException {
    Path path = Paths.get(project.getProjectDir().getAbsolutePath(), "build", "Dependency");
    File dependenciesDirectory = path.toFile();
    checkThenCollectDependencyClasses(dependenciesDirectory);
  }

  /**
   * It checks whether the provided directory exists or not.
   *
   * @param outputDirectory Directory from where classes has to be collected.
   * @throws IOException In case of IO issues.
   */
  private void checkThenCollectDependencyClasses(final File outputDirectory) throws IOException {
    if (outputDirectory.exists()) {
      collectDependencyClasses(outputDirectory);
    }
  }

  /**
   * Analyze the project's build dependency classes.
   *
   * @param outputDirectory File where dependency classes are stored.
   * @return set of classes referenced by visited class.
   * @throws IOException In case of IO issues.
   */
  private Set<String> collectDependencyClasses(final File outputDirectory) throws IOException {
    URL url = outputDirectory.toURI().toURL();
    return dependencyAnalyzer.analyze(url);
  }

  /**
   * Collects the classes referenced in the project's XML resource files (e.g. Spring XML
   * configurations, web.xml), so that dependencies only referenced from XML are considered used.
   *
   * @param project The gradle project.
   * @return The class names referenced in XML resources.
   */
  private Set<String> collectClassesFromXmlResources(final Project project) {
    Set<File> resourceDirectories = new LinkedHashSet<>();
    SourceSetContainer sourceSets = project.getExtensions().findByType(SourceSetContainer.class);
    if (sourceSets != null) {
      for (SourceSet sourceSet : sourceSets) {
        if (isIgnoredTest
            && sourceSet
                .getName()
                .toLowerCase(Locale.ROOT)
                .contains(SourceSet.TEST_SOURCE_SET_NAME)) {
          continue;
        }
        resourceDirectories.addAll(sourceSet.getResources().getSrcDirs());
      }
    }
    resourceDirectories.add(
        new File(
            project.getProjectDir(), "src" + File.separator + "main" + File.separator + "webapp"));
    Set<String> classes = new HashSet<>();
    for (File directory : resourceDirectories) {
      classes.addAll(new XmlClassesAnalyzer(directory.toPath()).collectReferencedClassesFromXml());
    }
    return classes;
  }

  /**
   * Determine the artifacts that are used.
   *
   * @param artifactClassMap A map of [artifact] -> [classes in the artifact].
   * @param referencedClasses A set of classes that are detected as used.
   * @return The set of used artifacts.
   */
  private Set<ResolvedArtifact> collectUsedArtifacts(
      final Map<ResolvedArtifact, Set<String>> artifactClassMap,
      final Set<String> referencedClasses) {
    // Invert the map once; the first artifact declaring a class wins, as in the previous scan.
    Map<String, ResolvedArtifact> artifactByClass = new HashMap<>();
    for (Map.Entry<ResolvedArtifact, Set<String>> entry : artifactClassMap.entrySet()) {
      for (String className : entry.getValue()) {
        artifactByClass.putIfAbsent(className, entry.getKey());
      }
    }
    Set<ResolvedArtifact> usedArtifacts = new HashSet<>();
    for (String clazz : referencedClasses) {
      ResolvedArtifact artifact = artifactByClass.get(clazz);
      if (artifact != null) {
        artifactUsedClassesMap.computeIfAbsent(artifact, k -> new HashSet<>()).add(clazz);
        usedArtifacts.add(artifact);
      }
    }
    return usedArtifacts;
  }

  /**
   * This method defines a new way to remove the artifacts by using the conflict id. We don't care
   * about the version here because there can be only 1 for a given artifact anyway.
   *
   * @param start initial set
   * @param remove set to exclude
   * @return set with remove excluded
   */
  private Set<ResolvedArtifact> removeAll(
      final Set<ResolvedArtifact> start, final Set<ResolvedArtifact> remove) {
    Set<ResolvedArtifact> results = new LinkedHashSet<>(start.size());
    for (ResolvedArtifact artifact : start) {
      boolean found = false;
      for (ResolvedArtifact artifact2 : remove) {
        if (artifact.getId().equals(artifact2.getId())
            && artifact.getName().equals(artifact2.getName())) {
          found = true;
          break;
        }
      }
      if (!found) {
        results.add(artifact);
      }
    }
    return results;
  }

  /**
   * Computes a map of [dependency] -> [allTypes, usedTypes]. The result is computed once per
   * analysis; callers may invoke this method freely.
   *
   * @return A map of [dependency] -> [allTypes, usedTypes]
   */
  public Map<String, DependencyTypes> getDependenciesClassesMap() {
    if (dependenciesClassesMap == null) {
      dependenciesClassesMap = buildDependenciesClassesMap();
    }
    return dependenciesClassesMap;
  }

  private Map<String, DependencyTypes> buildDependenciesClassesMap() {
    // the output
    Map<String, DependencyTypes> dependenciesClassMap = new HashMap<>();
    // iterate through all the resolved artifacts
    for (Map.Entry<ResolvedArtifact, Set<String>> entry : artifactClassesMap.entrySet()) {
      ResolvedArtifact resolvedArtifact = entry.getKey();
      // all the types in all artifacts
      Set<ClassName> allClassNameSet = new HashSet<>();
      for (String type : entry.getValue()) {
        allClassNameSet.add(new ClassName(type));
      }
      // all the types in used artifacts
      Set<ClassName> usedClassNameSet = new HashSet<>();
      for (String type : artifactUsedClassesMap.getOrDefault(resolvedArtifact, new HashSet<>())) {
        usedClassNameSet.add(new ClassName(type));
      }
      dependenciesClassMap.put(
          resolvedArtifact.getModuleVersion().toString(),
          new DependencyTypes(allClassNameSet, usedClassNameSet));
    }
    return dependenciesClassMap;
  }
}
