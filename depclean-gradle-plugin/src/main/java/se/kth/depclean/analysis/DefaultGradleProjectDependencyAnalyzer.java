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
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import se.kth.depclean.core.analysis.DependencyTypes;
import se.kth.depclean.core.analysis.ClassAnalyzer;
import se.kth.depclean.core.analysis.DefaultClassAnalyzer;
import se.kth.depclean.core.analysis.DependencyAnalyzer;
import se.kth.depclean.core.analysis.ProjectDependencyAnalyzerException;
import se.kth.depclean.core.analysis.asm.ASMDependencyAnalyzer;
import se.kth.depclean.core.analysis.graph.DefaultCallGraph;
import se.kth.depclean.core.model.ClassName;
import se.kth.depclean.utils.DependencyUtils;

/**
 * This is principal class that perform the dependency analysis in a Gradle project.
 */
@Slf4j
@Component(role = GradleProjectDependencyAnalyzer.class)
public class DefaultGradleProjectDependencyAnalyzer
        implements GradleProjectDependencyAnalyzer {

  @Requirement
  private final ClassAnalyzer classAnalyzer = new DefaultClassAnalyzer();

  @Requirement
  private final DependencyAnalyzer dependencyAnalyzer = new ASMDependencyAnalyzer();

  /**
   * If true, the project's classes in target/test-classes are not going to be analyzed.
   */
  private final boolean isIgnoredTest;

  /**
   * A map [artifact] -> [allTypes].
   */
  private Map<ResolvedArtifact, Set<String>> artifactClassesMap;

  /**
   * A map [artifact] -> [usedTypes].
   */
  private final Map<ResolvedArtifact, Set<String>> artifactUsedClassesMap = new HashMap<>();

  /**
   * Ctor.
   */
  public DefaultGradleProjectDependencyAnalyzer(final boolean isIgnoredTest) {
    this.isIgnoredTest = isIgnoredTest;
  }

  /**
   * Analyze the dependencies in a project.
   *
   * @param project The Gradle project to be analyzed.
   * @return An object with the usedDeclaredArtifacts, usedUndeclaredArtifacts,
   *        and unusedDeclaredArtifacts.
   * @throws ProjectDependencyAnalyzerException if the analysis fails.
   * @see <code>ProjectDependencyAnalyzer#analyze(org.apache.invoke.project.MavenProject)</code>
   */
  @Override
  public GradleProjectDependencyAnalysis analyze(final Project project)
          throws ProjectDependencyAnalyzerException {
    try {
      // project's configurations.
      ConfigurationContainer configurationContainer = project.getConfigurations();
      Set<Configuration> configurations = new HashSet<>(configurationContainer);

      DependencyUtils utils = new DependencyUtils();
      // all resolved dependencies including transitive ones of the project.
      Set<ResolvedDependency> allDependencies = utils.getAllDependencies(configurations);

      // all resolved artifacts of this project
      Set<ResolvedArtifact> allArtifacts = new HashSet<>();
      for (ResolvedDependency dependency : allDependencies) {
        allArtifacts.addAll(dependency.getModuleArtifacts());
      }

      // a map of [dependency] -> [classes]
      artifactClassesMap = buildArtifactClassMap(allArtifacts);

      // direct dependencies of the project
      Set<ResolvedDependency> declaredDependencies = utils.getDeclaredDependencies(configurations);

      // direct artifacts of the project
      Set<ResolvedArtifact> declaredArtifacts = utils.getDeclaredArtifacts(declaredDependencies);

      /* ******************** bytecode analysis ********************* */

      // execute the analysis (note that the order of these operations matters!)
      buildProjectDependencyClasses(project);
      Set<String> projectClasses = new HashSet<>(DefaultCallGraph.getProjectVertices());
      buildDependenciesDependencyClasses(project);

      /* ******************** usage analysis ********************* */

      // search for the dependencies used by the project
      Set<ResolvedArtifact> usedArtifacts = collectUsedArtifacts(
              artifactClassesMap,
              DefaultCallGraph.referencedClassMembers(projectClasses)
      );

      /* ******************** results as statically used at the bytecode *********************** */

      // for the used dependencies, get the ones that are declared
      Set<ResolvedArtifact> usedDeclaredArtifacts = new LinkedHashSet<>(declaredArtifacts);
      usedDeclaredArtifacts.retainAll(usedArtifacts);

      // for the used dependencies, remove the ones that are declared
      Set<ResolvedArtifact> usedUndeclaredArtifacts = new LinkedHashSet<>(usedArtifacts);
      usedUndeclaredArtifacts = removeAll(usedUndeclaredArtifacts, declaredArtifacts);

      // for the declared dependencies, get the ones that are not used
      Set<ResolvedArtifact> unusedDeclaredArtifacts = new LinkedHashSet<>(declaredArtifacts);
      unusedDeclaredArtifacts = removeAll(unusedDeclaredArtifacts, usedArtifacts);

      return new GradleProjectDependencyAnalysis(usedDeclaredArtifacts,
              usedUndeclaredArtifacts, unusedDeclaredArtifacts);

    } catch (IOException e) {
      throw new ProjectDependencyAnalyzerException("Cannot analyze dependencies", e);
    }
  }

  /**
   * Returns a map with the artifacts (dependencies) in a Gradle project and
   * their corresponding classes.
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
        try (JarFile jarFile = new JarFile(file)) {
          Enumeration<JarEntry> jarEntries = jarFile.entries();
          Set<String> classes = new HashSet<>();
          while (jarEntries.hasMoreElements()) {
            String entry = jarEntries.nextElement().getName();
            if (entry.endsWith(".class")) {
              String className = entry.replace('/', '.');
              className = className.substring(0, className.length() - ".class".length());
              classes.add(className);
            }
          }
          artifactClassMap.put(artifact, classes);
        }
      } else if (file.isDirectory()) {
        URL url = file.toURI().toURL();
        Set<String> classes = classAnalyzer.analyze(url);
        artifactClassMap.put(artifact, classes);
      }
    }
    return artifactClassMap;
  }

  /**
   * Get the project's build classes.
   *
   * @param project The gradle project.
   * @throws IOException In case of IO issues.
   */
  private void  buildProjectDependencyClasses(final Project project) throws IOException {
    Path classesDir = Paths.get(project.getProjectDir().getAbsolutePath(), "build", "classes");

    // Analyze src classes in the project
    File outputJavaDir = classesDir.resolve("java").resolve("main").toFile();
    File outputGroovyDir = classesDir.resolve("groovy").resolve("main").toFile();
    File outputKotlinDir = classesDir.resolve("kotlin").resolve("main").toFile();
    checkThenCollectDependencyClasses(outputJavaDir);
    checkThenCollectDependencyClasses(outputGroovyDir);
    checkThenCollectDependencyClasses(outputKotlinDir);

    // Analyze test classes in the project
    if (!isIgnoredTest) {
      File testOutputJavaDir = classesDir.resolve("java").resolve("test").toFile();
      File testOutputGroovyDir = classesDir.resolve("groovy").resolve("test").toFile();
      File testOutputKotlinDir = classesDir.resolve("kotlin").resolve("test").toFile();
      checkThenCollectDependencyClasses(testOutputJavaDir);
      checkThenCollectDependencyClasses(testOutputGroovyDir);
      checkThenCollectDependencyClasses(testOutputKotlinDir);
    }
  }

  /**
   * Get the project's build dependency classes.
   *
   * @param project The gradle project.
   * @throws IOException In case of IO issues.
   */
  private void buildDependenciesDependencyClasses(final Project project) throws IOException {
    Path path = Paths.get(project.getProjectDir().getAbsolutePath(),"build", "Dependency");
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
   * Determine the artifacts that are used.
   *
   * @param artifactClassMap  A map of [artifact] -> [classes in the artifact].
   * @param referencedClasses A set of classes that are detected as used.
   * @return The set of used artifacts.
   */
  private Set<ResolvedArtifact> collectUsedArtifacts(
          final Map<ResolvedArtifact, Set<String>> artifactClassMap,
          final Set<String> referencedClasses) {
    Set<ResolvedArtifact> usedArtifacts = new HashSet<>();
    for (String clazz : referencedClasses) {
      ResolvedArtifact artifact = findArtifactForClassName(artifactClassMap, clazz);
      if (findArtifactForClassName(artifactClassMap, clazz) != null) {
        if (!artifactUsedClassesMap.containsKey(artifact)) {
          artifactUsedClassesMap.put(artifact, new HashSet<>());
        }
        artifactUsedClassesMap.get(artifact).add(clazz);
        usedArtifacts.add(artifact);
      }
    }
    return usedArtifacts;
  }

  /**
   * Utility method to find whether a provided key is present in the map or not.
   *
   * @param artifactClassMap The map
   * @param className The String (Expected key)
   * @return Key if it is present otherwise null.
   */
  private ResolvedArtifact findArtifactForClassName(
          final Map<ResolvedArtifact, Set<String>> artifactClassMap,
          final String className) {
    for (Map.Entry<ResolvedArtifact, Set<String>> entry : artifactClassMap.entrySet()) {
      if (entry.getValue().contains(className)) {
        return entry.getKey();
      }
    }
    return null;
  }

  /**
   * This method defines a new way to remove the artifacts by using the conflict id.
   * We don't care about the version here because there can be only 1 for a given artifact anyway.
   *
   * @param start  initial set
   * @param remove set to exclude
   * @return set with remove excluded
   */
  private Set<ResolvedArtifact> removeAll(
          final Set<ResolvedArtifact> start,
          final Set<ResolvedArtifact> remove) {
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
    // the output
    Map<String, DependencyTypes> dependenciesClassMap = new HashMap<>();
    // iterate through all the resolved artifacts
    for (Map.Entry<ResolvedArtifact, Set<String>> entry : artifactClassesMap.entrySet()) {
      ResolvedArtifact resolvedArtifact = entry.getKey();
      // all the types in all artifacts
      Set<String> typesSet = artifactClassesMap.get(resolvedArtifact);
      if (typesSet == null) {
        typesSet = new HashSet<>();
      }
      Set<ClassName> allClassNameSet = new HashSet<>();
      for (String type : typesSet) {
        allClassNameSet.add(new ClassName(type));
      }
      // all the types in used artifacts
      Set<String> usedTypesSet = artifactUsedClassesMap.get(resolvedArtifact);
      if (usedTypesSet == null) {
        usedTypesSet = new HashSet<>();
      }
      Set<ClassName> usedClassNameSet = new HashSet<>();
      for (String type : usedTypesSet) {
        usedClassNameSet.add(new ClassName(type));
      }

      if (artifactUsedClassesMap.containsKey(resolvedArtifact)) {
        dependenciesClassMap
            .put(resolvedArtifact.getModuleVersion().toString(),
            new DependencyTypes(
                allClassNameSet, // get all the typesSet
                usedClassNameSet // get used typesSet
            ));
      } else {
        dependenciesClassMap
            .put(resolvedArtifact.getModuleVersion().toString(),
            new DependencyTypes(
                allClassNameSet, // get all the typesSet
                new HashSet<>() // get used typesSet
            ));
      }
    }
    return dependenciesClassMap;
  }
}
