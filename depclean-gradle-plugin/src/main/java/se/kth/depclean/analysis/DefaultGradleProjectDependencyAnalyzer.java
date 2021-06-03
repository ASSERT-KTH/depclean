package se.kth.depclean.analysis;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import se.kth.depclean.core.analysis.ArtifactTypes;
import se.kth.depclean.core.analysis.ClassAnalyzer;
import se.kth.depclean.core.analysis.DefaultClassAnalyzer;
import se.kth.depclean.core.analysis.DependencyAnalyzer;
import se.kth.depclean.core.analysis.ProjectDependencyAnalyzerException;
import se.kth.depclean.core.analysis.asm.ASMDependencyAnalyzer;
import se.kth.depclean.core.analysis.graph.DefaultCallGraph;

/**
 * This is principal class that perform the dependency analysis in a Gradle project.
 */
@Slf4j
@Component(role = GradleProjectDependencyAnalyzer.class)
public class DefaultGradleProjectDependencyAnalyzer implements GradleProjectDependencyAnalyzer {

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
  public DefaultGradleProjectDependencyAnalyzer(boolean isIgnoredTest) {
    this.isIgnoredTest = isIgnoredTest;
  }

  /**
   * Analyze the dependencies in a project.
   *
   * @param project The Gradle project to be analyzed.
   * @return An object with the usedDeclaredArtifacts, usedUndeclaredArtifacts, and unusedDeclaredArtifacts.
   * @throws ProjectDependencyAnalyzerException if the analysis fails.
   * @see <code>ProjectDependencyAnalyzer#analyze(org.apache.invoke.project.MavenProject)</code>
   */
  @Override
  public GradleProjectDependencyAnalysis analyze(Project project) throws ProjectDependencyAnalyzerException {
    try {
      ConfigurationContainer configurationContainer = project.getConfigurations();

      // project's configurations.
      Set<Configuration> configurations = new HashSet<>(configurationContainer);

      // all resolved dependencies including transitive ones of the project.
      Set<ResolvedDependency> allDependencies = getAllDependencies(configurations);

      // all resolved artifacts of this project
      Set<ResolvedArtifact> allArtifacts = getAllArtifacts(allDependencies);

      // a map of [dependency] -> [classes]
      artifactClassesMap = buildArtifactClassMap(allArtifacts);

      // direct dependencies of the project
      Set<ResolvedDependency> declaredDependencies = getDeclaredDependencies(configurations);

      // direct artifacts of the project
      Set<ResolvedArtifact> declaredArtifacts = getDeclaredArtifacts(declaredDependencies);

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

      return new GradleProjectDependencyAnalysis(allArtifacts, usedDeclaredArtifacts, usedUndeclaredArtifacts, unusedDeclaredArtifacts);

    } catch (IOException e) {
      throw new ProjectDependencyAnalyzerException("Cannot analyze dependencies", e);
    }
  }

  /**
   * Returns all the dependencies of the project.
   *
   * @param configurations All the configuration used in the project.
   * @return A set of all dependencies.
   */
  @NonNull
  public Set<ResolvedDependency> getAllDependencies(Set<Configuration> configurations) {
    Set<ResolvedDependency> allDependencies = new HashSet<>();
    for (Configuration configuration : configurations) {
//      configuration.setCanBeResolved(true);
      allDependencies.addAll(configuration.getResolvedConfiguration().getFirstLevelModuleDependencies());
    }
    Set<ResolvedDependency> children = new HashSet<>();
    for (ResolvedDependency dependency : allDependencies) {
      children.addAll(dependency.getChildren());
    }
    allDependencies.addAll(children);
    return allDependencies;
  }
  /**
   * Returns all the artifacts of the project.
   *
   * @param allDependencies All dependencies of the project.
   * @return A set of all artifacts.
   */
  @NonNull
  public Set<ResolvedArtifact> getAllArtifacts(Set<ResolvedDependency> allDependencies) {
    Set<ResolvedArtifact> allArtifacts = new HashSet<>();
    for (ResolvedDependency dependency : allDependencies) {
      allArtifacts.addAll(dependency.getAllModuleArtifacts());
    }
    return allArtifacts;
  }

  /**
   * Returns a map with the artifacts (dependencies) in a Gradle project and their corresponding classes.
   *
   * @param allArtifacts File of each artifact.
   * @return A map of artifact -> classes.
   * @throws IOException If the class cannot be analyzed.
   */
  public Map<ResolvedArtifact, Set<String>> buildArtifactClassMap(Set<ResolvedArtifact> allArtifacts) throws IOException {
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
   * Returns all the dependencies of the project.
   *
   * @param configurations All the configuration used in the project.
   * @return A set of all dependencies.
   */
  @NonNull
  public Set<ResolvedDependency> getDeclaredDependencies(Set<Configuration> configurations) {
    Set<ResolvedDependency> declaredDependency = new HashSet<>();
    for (Configuration configuration : configurations) {
//      configuration.setCanBeResolved(true);
      declaredDependency.addAll(configuration.getResolvedConfiguration().getFirstLevelModuleDependencies());
    }
    return declaredDependency;
  }
  /**
   * To get the artifacts which are declared in the project.
   *
   * @param declaredDependency Project's configuration.
   * @return A set of declared artifacts.
   */
  public Set<ResolvedArtifact> getDeclaredArtifacts(Set<ResolvedDependency> declaredDependency) {
    Set<ResolvedArtifact> declaredArtifacts = new HashSet<>();
    for (ResolvedDependency dependency : declaredDependency) {
      declaredArtifacts.addAll(dependency.getAllModuleArtifacts());
    }
    return declaredArtifacts;
  }

  /**
   * Get the project's build classes.
   *
   * @param project The gradle project.
   * @throws IOException In case of IO issues.
   */
  private void  buildProjectDependencyClasses(Project project) throws IOException {
    String sep = File.separator;
    String classesDir = project.getProjectDir().getAbsolutePath() +
                                    sep + "build" +
                                    sep + "classes";
    // Analyze src classes in the project
    File outputJavaDir = new File( classesDir + sep + "java" + sep + "main");
    File outputGroovyDir = new File(classesDir + sep + "groovy" + sep + "main");
    File outputKotlinDir = new File(classesDir + sep + "kotlin" + sep + "main");
    checkThenCollectDependencyClasses(outputJavaDir);
    checkThenCollectDependencyClasses(outputGroovyDir);
    checkThenCollectDependencyClasses(outputKotlinDir);
    // Analyze test classes in the project
    if (!isIgnoredTest) {
      File testOutputJavaDir = new File( classesDir + sep + "java" + sep + "test");
      File testOutputGroovyDir = new File(classesDir + sep + "groovy" + sep + "test");
      File testOutputKotlinDir = new File(classesDir + sep + "kotlin" + sep + "test");
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
  private void buildDependenciesDependencyClasses(Project project) throws IOException {
    File dependenciesDirectory = new File(project.getProjectDir().getAbsolutePath() +
                                          File.separator + "build" +
                                          File.separator + "dependency");
    checkThenCollectDependencyClasses(dependenciesDirectory);
  }

  /**
   * It checks whether the provided directory exists or not.
   *
   * @param outputDirectory Directory from where classes has to be collected.
   * @throws IOException In case of IO issues.
   */
  private void checkThenCollectDependencyClasses(File outputDirectory) throws IOException {
    if(outputDirectory.exists()) {
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
   private Set<String> collectDependencyClasses(File outputDirectory) throws IOException {
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
      Map<ResolvedArtifact, Set<String>> artifactClassMap,
      Set<String> referencedClasses) {
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

  private ResolvedArtifact findArtifactForClassName(Map<ResolvedArtifact, Set<String>> artifactClassMap, String className) {
    for (Map.Entry<ResolvedArtifact, Set<String>> entry : artifactClassMap.entrySet()) {
      if (entry.getValue().contains(className)) {
        return entry.getKey();
      }
    }
    return null;
  }


  /**
   * This method defines a new way to remove the artifacts by using the conflict id. We don't care about the version
   * here because there can be only 1 for a given artifact anyway.
   *
   * @param start  initial set
   * @param remove set to exclude
   * @return set with remove excluded
   */
  private Set<ResolvedArtifact> removeAll(Set<ResolvedArtifact> start, Set<ResolvedArtifact> remove) {
    Set<ResolvedArtifact> results = new LinkedHashSet<>(start.size());
    for (ResolvedArtifact artifact : start) {
      boolean found = false;
      for (ResolvedArtifact artifact2 : remove) {
        if (artifact.getId().equals(artifact2.getId()) && artifact.getName().equals(artifact2.getName())) {
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
   * Computes a map of [artifact] -> [allTypes, usedTypes].
   *
   * @return A map of [artifact] -> [allTypes, usedTypes]
   */
  public Map<String, ArtifactTypes> getArtifactClassesMap() {
    Map<String, ArtifactTypes> output = new HashMap<>();
    for (Map.Entry<ResolvedArtifact, Set<String>> entry : artifactClassesMap.entrySet()) {
      ResolvedArtifact key = entry.getKey();
      if (artifactUsedClassesMap.containsKey(key)) {
        output.put(key.toString(),
            new ArtifactTypes(
                artifactClassesMap.get(key), // get all the types
                artifactUsedClassesMap.get(key) // get used types
            ));
      } else {
        output.put(key.toString(),
            new ArtifactTypes(
                artifactClassesMap.get(key), // get all the types
                new HashSet<>() // get used types
            ));
      }
    }
    return output;
  }
}
