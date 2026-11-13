package se.kth.depclean.analysis;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.jspecify.annotations.Nullable;
import se.kth.depclean.core.analysis.ClassUsageAnalyzer;
import se.kth.depclean.core.analysis.DependencyTypes;
import se.kth.depclean.core.analysis.graph.ClassUsageGraph;
import se.kth.depclean.core.analysis.src.XmlClassesAnalyzer;
import se.kth.depclean.core.model.ClassName;
import se.kth.depclean.core.util.ClassFiles;
import se.kth.depclean.utils.ClassesDirectoryFinder;
import se.kth.depclean.utils.DependencyUtils;

/** This is principal class that perform the dependency analysis in a Gradle project. */
public class DefaultGradleProjectDependencyAnalyzer {

  private final ClassUsageAnalyzer classUsageAnalyzer = new ClassUsageAnalyzer();

  /** If true, the project's classes in src/test are not going to be analyzed. */
  private final boolean isIgnoredTest;

  /** A map [artifact] -> [allTypes]. */
  private Map<ResolvedArtifact, Set<String>> artifactClassesMap = new HashMap<>();

  /** A map [artifact] -> [usedTypes]. */
  private final Map<ResolvedArtifact, Set<String>> artifactUsedClassesMap = new HashMap<>();

  /**
   * The [dependency] -> [types] map of the last analysis, built on demand and cached because it is
   * looked up once for every node of the dependency tree when the JSON report is written.
   */
  @Nullable private Map<String, DependencyTypes> dependenciesClassesMap;

  /** The references between the classes reachable from the project, as found by the analysis. */
  private ClassUsageGraph classUsageGraph = ClassUsageGraph.empty();

  /**
   * Ctor.
   *
   * @param isIgnoredTest If true, the project's classes in src/test are not going to be analyzed.
   */
  public DefaultGradleProjectDependencyAnalyzer(final boolean isIgnoredTest) {
    this.isIgnoredTest = isIgnoredTest;
  }

  /**
   * Analyze the dependencies in a project.
   *
   * @param project The Gradle project to be analyzed.
   * @return An object with the usedDeclaredArtifacts, usedUndeclaredArtifacts, and
   *     unusedDeclaredArtifacts.
   */
  public GradleProjectDependencyAnalysis analyze(final Project project) {
    // Use the filtered resolvable configurations instead of all configurations
    DependencyUtils utils = new DependencyUtils();
    Set<Configuration> configurations = utils.getResolvableConfigurations(project);

    // all resolved dependencies including transitive ones of the project.
    Set<ResolvedDependency> allDependencies = utils.getAllDependencies(configurations);

    // all resolved artifacts of this project
    Set<ResolvedArtifact> allArtifacts = new LinkedHashSet<>();
    for (ResolvedDependency dependency : allDependencies) {
      allArtifacts.addAll(dependency.getModuleArtifacts());
    }

    // a map of [dependency] -> [classes]
    artifactClassesMap = buildArtifactClassMap(allArtifacts);
    artifactUsedClassesMap.clear();
    dependenciesClassesMap = null;

    // direct dependencies of the project
    Set<ResolvedDependency> declaredDependencies = utils.getDeclaredDependencies(configurations);

    // direct artifacts of the project
    Set<ResolvedArtifact> declaredArtifacts = utils.getDeclaredArtifacts(declaredDependencies);

    /* ******************** bytecode analysis ********************* */

    // The classes of the project (all source sets and layouts: JVM, custom, Android variants) are
    // analyzed in full. The classes of the dependencies are read straight from their jars, and
    // only the ones that the project can reach.
    List<Path> projectClassDirectories = new ArrayList<>();
    for (File classesDir : ClassesDirectoryFinder.findClassesDirectories(project, isIgnoredTest)) {
      projectClassDirectories.add(classesDir.toPath());
    }
    List<File> classpath = new ArrayList<>();
    for (ResolvedArtifact artifact : allArtifacts) {
      classpath.add(artifact.getFile());
    }
    classUsageGraph = classUsageAnalyzer.analyze(projectClassDirectories, classpath);

    /* ******************** usage analysis ********************* */

    // classes referenced in the bytecode, plus classes referenced in XML resources
    // (e.g. Spring XML configurations, web.xml), see issues #78 and #81
    Set<String> referencedClasses = new HashSet<>(classUsageGraph.getReachableClasses());
    referencedClasses.addAll(collectClassesFromXmlResources(project));

    // search for the dependencies used by the project
    Set<ResolvedArtifact> usedArtifacts =
        collectUsedArtifacts(artifactClassesMap, referencedClasses);

    /* ******************** results as statically used at the bytecode *********************** */

    // for the used dependencies, get the ones that are declared
    Set<ResolvedArtifact> usedDeclaredArtifacts = new LinkedHashSet<>(declaredArtifacts);
    usedDeclaredArtifacts.retainAll(usedArtifacts);

    // for the used dependencies, remove the ones that are declared
    Set<ResolvedArtifact> usedUndeclaredArtifacts = removeAll(usedArtifacts, declaredArtifacts);

    // for the declared dependencies, get the ones that are not used
    Set<ResolvedArtifact> unusedDeclaredArtifacts = removeAll(declaredArtifacts, usedArtifacts);

    return new GradleProjectDependencyAnalysis(
        usedDeclaredArtifacts, usedUndeclaredArtifacts, unusedDeclaredArtifacts);
  }

  /**
   * Maps every resolved artifact to the classes it contains.
   *
   * @param allArtifacts the artifacts to index
   * @return a map of [artifact] -> [fully qualified class names]
   */
  public Map<ResolvedArtifact, Set<String>> buildArtifactClassMap(
      final Set<ResolvedArtifact> allArtifacts) {
    Map<ResolvedArtifact, Set<String>> artifactClassMap = new LinkedHashMap<>();
    for (ResolvedArtifact artifact : allArtifacts) {
      File file = artifact.getFile();
      if (file.getName().endsWith(".jar") || file.isDirectory()) {
        artifactClassMap.put(artifact, new HashSet<>(ClassFiles.list(file).keySet()));
      }
    }
    return artifactClassMap;
  }

  /**
   * The references between the classes reachable from the project, as found by the last analysis.
   *
   * @return the class usage graph, empty before the first analysis
   */
  public ClassUsageGraph getClassUsageGraph() {
    return classUsageGraph;
  }

  /**
   * Collects the classes referenced in the XML resources of the project (e.g. Spring XML
   * configurations and web.xml deployment descriptors). The resources directories of all the source
   * sets are scanned, together with the conventional web application directory. The test source
   * sets are skipped when the tests are ignored.
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
    // Invert the map once; the first artifact declaring a class wins.
    Map<String, ResolvedArtifact> artifactByClass = new HashMap<>();
    for (Map.Entry<ResolvedArtifact, Set<String>> entry : artifactClassMap.entrySet()) {
      for (String className : entry.getValue()) {
        artifactByClass.putIfAbsent(className, entry.getKey());
      }
    }
    Set<ResolvedArtifact> usedArtifacts = new LinkedHashSet<>();
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
   * Computes a new set after removing some items from a set of artifacts. Artifacts are compared by
   * their id and name.
   *
   * @param start initial set of artifacts.
   * @param remove set of artifacts which must be removed.
   * @return set of artifacts which are not removed.
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
   * Computes a map of [dependency] -> [allTypes, usedTypes].
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
    Map<String, DependencyTypes> dependenciesClassMap = new HashMap<>();
    for (Map.Entry<ResolvedArtifact, Set<String>> entry : artifactClassesMap.entrySet()) {
      ResolvedArtifact resolvedArtifact = entry.getKey();
      Set<ClassName> allClassNameSet =
          entry.getValue().stream().map(ClassName::new).collect(Collectors.toSet());
      Set<ClassName> usedClassNameSet =
          artifactUsedClassesMap.getOrDefault(resolvedArtifact, new HashSet<>()).stream()
              .map(ClassName::new)
              .collect(Collectors.toSet());
      dependenciesClassMap.put(
          resolvedArtifact.getModuleVersion().toString(),
          new DependencyTypes(allClassNameSet, usedClassNameSet));
    }
    return dependenciesClassMap;
  }
}
