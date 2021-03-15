/*
 * Copyright (c) 2020, CASTOR Software Research Centre (www.castor.kth.se)
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package se.kth.depclean.core.analysis;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import se.kth.depclean.core.analysis.asm.ASMDependencyAnalyzer;
import se.kth.depclean.core.analysis.graph.DefaultCallGraph;

/**
 * The principal class that perform the dependency analysis in a Maven project.
 */
@Component(role = ProjectDependencyAnalyzer.class)
public class DefaultProjectDependencyAnalyzer implements ProjectDependencyAnalyzer {

  /**
   * If true, the project's classes in target/test-classes are not going to be analyzed.
   */
  private final boolean isIgnoredTest;

  @Requirement
  private final ClassAnalyzer classAnalyzer = new DefaultClassAnalyzer();

  @Requirement
  private final DependencyAnalyzer dependencyAnalyzer = new ASMDependencyAnalyzer();

  private final Map<Artifact, Set<String>> artifactUsedClassesMap = new HashMap<>();

  /**
   * Ctor.
   */
  public DefaultProjectDependencyAnalyzer(boolean isIgnoredTest) {
    this.isIgnoredTest = isIgnoredTest;
  }

  /**
   * A map [dependency] -> [dependency classes].
   */
  private Map<Artifact, Set<String>> artifactClassesMap;

  /**
   * Analyze the dependencies in a project.
   *
   * @param project The Maven project to be analyzed.
   * @return An object with the usedDeclaredArtifacts, usedUndeclaredArtifacts, and unusedDeclaredArtifacts.
   * @throws ProjectDependencyAnalyzerException if the analysis fails.
   * @see <code>ProjectDependencyAnalyzer#analyze(org.apache.invoke.project.MavenProject)</code>
   */
  public ProjectDependencyAnalysis analyze(MavenProject project) throws ProjectDependencyAnalyzerException {
    try {
      // a map of [dependency] -> [classes]
      artifactClassesMap = buildArtifactClassMap(project);

      // direct dependencies of the project
      Set<Artifact> declaredArtifacts = project.getDependencyArtifacts();

      // transitive dependencies of the project
      Set<Artifact> transitiveArtifacts = removeAll(project.getArtifacts(), declaredArtifacts);

      /* ******************** bytecode analysis ********************* */

      // execute the analysis (note that the order of these operations matters!)
      buildProjectDependencyClasses(project);
      Set<String> projectClasses = new HashSet<>(DefaultCallGraph.getProjectVertices());
      buildDependenciesDependencyClasses(project);

      /* ******************** usage analysis ********************* */

      // search for the dependencies used by the project
      Set<Artifact> usedArtifacts = collectUsedArtifacts(
          artifactClassesMap,
          DefaultCallGraph.referencedClassMembers(projectClasses)
      );

      /* ******************** results as statically used at the bytecode *********************** */

      // for the used dependencies, get the ones that are declared
      Set<Artifact> usedDeclaredArtifacts = new LinkedHashSet<>(declaredArtifacts);
      usedDeclaredArtifacts.retainAll(usedArtifacts);

      // for the used dependencies, remove the ones that are declared
      Set<Artifact> usedUndeclaredArtifacts = new LinkedHashSet<>(usedArtifacts);
      usedUndeclaredArtifacts = removeAll(usedUndeclaredArtifacts, declaredArtifacts);

      // for the declared dependencies, get the ones that are not used
      Set<Artifact> unusedDeclaredArtifacts = new LinkedHashSet<>(declaredArtifacts);
      unusedDeclaredArtifacts = removeAll(unusedDeclaredArtifacts, usedArtifacts);

      return new ProjectDependencyAnalysis(usedDeclaredArtifacts, usedUndeclaredArtifacts, unusedDeclaredArtifacts);
    } catch (IOException exception) {
      throw new ProjectDependencyAnalyzerException("Cannot analyze dependencies", exception);
    }
  }

  /**
   * Returns a map with the artifacts (dependencies) in a Maven project and their corresponding classes.
   *
   * @param project A Maven project.
   * @return A map of artifact -> classes.
   * @throws IOException If the class cannot be analyzed.
   */
  public Map<Artifact, Set<String>> buildArtifactClassMap(MavenProject project) throws IOException {
    Map<Artifact, Set<String>> artifactClassMap = new LinkedHashMap<>();
    Set<Artifact> dependencyArtifacts = project.getArtifacts();
    for (Artifact artifact : dependencyArtifacts) {
      File file = artifact.getFile();
      if (file != null && file.getName().endsWith(".jar")) {
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
      } else if (file != null && file.isDirectory()) {
        URL url = file.toURI().toURL();
        Set<String> classes = classAnalyzer.analyze(url);
        artifactClassMap.put(artifact, classes);
      }
    }
    return artifactClassMap;
  }

  private void buildProjectDependencyClasses(MavenProject project) throws IOException {
    // Analyze src classes in the project
    String outputDirectory = project.getBuild().getOutputDirectory();
    collectDependencyClasses(outputDirectory);
    // Analyze test classes in the project
    if (!isIgnoredTest) {
      String testOutputDirectory = project.getBuild().getTestOutputDirectory();
      collectDependencyClasses(testOutputDirectory);
    }
  }

  private void buildDependenciesDependencyClasses(MavenProject project) throws IOException {
    String dependenciesDirectory = project.getBuild().getDirectory() + File.separator + "dependency";
    collectDependencyClasses(dependenciesDirectory);
  }

  private Set<Artifact> collectUsedArtifacts(Map<Artifact, Set<String>> artifactClassMap,
      Set<String> referencedClasses) {
    Set<Artifact> usedArtifacts = new HashSet<>();
    // find for used members in each class in the dependency classes
    for (String clazz : referencedClasses) {
      Artifact artifact = findArtifactForClassName(artifactClassMap, clazz);
      if (artifact != null) {
        if (!artifactUsedClassesMap.containsKey(artifact)) {
          artifactUsedClassesMap.put(artifact, new HashSet<>());
        }
        artifactUsedClassesMap.get(artifact).add(clazz);
        usedArtifacts.add(artifact);
      }
    }
    return usedArtifacts;
  }

  private Artifact findArtifactForClassName(Map<Artifact, Set<String>> artifactClassMap, String className) {
    for (Map.Entry<Artifact, Set<String>> entry : artifactClassMap.entrySet()) {
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
  private Set<Artifact> removeAll(Set<Artifact> start, Set<Artifact> remove) {
    Set<Artifact> results = new LinkedHashSet<>(start.size());
    for (Artifact artifact : start) {
      boolean found = false;
      for (Artifact artifact2 : remove) {
        if (artifact.getDependencyConflictId().equals(artifact2.getDependencyConflictId())) {
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

  private Set<String> collectDependencyClasses(String path) throws IOException {
    URL url = new File(path).toURI().toURL();
    return dependencyAnalyzer.analyze(url);
  }

  /**
   * Computes a map of artifacts and their types.
   *
   * @return A map of artifact -> classes
   */
  public Map<String, ArtifactTypes> getArtifactClassesMap() {
    Map<String, ArtifactTypes> output = new HashMap<>();
    for (Map.Entry<Artifact, Set<String>> entry : artifactClassesMap.entrySet()) {
      Artifact key = entry.getKey();
      if (artifactUsedClassesMap.containsKey(key)) {
        output.put(key.toString(), new ArtifactTypes(
            artifactClassesMap.get(key), // get all the types
            artifactUsedClassesMap.get(key) // get used types
        ));
      } else {
        output.put(key.toString(), new ArtifactTypes(
            artifactClassesMap.get(key), // get all the types
            new HashSet<>() // get used types
        ));
      }
    }
    return output;
  }
}

