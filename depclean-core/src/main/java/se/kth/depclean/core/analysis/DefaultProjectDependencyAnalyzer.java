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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import se.kth.depclean.core.analysis.asm.ASMDependencyAnalyzer;
import se.kth.depclean.core.analysis.graph.DefaultCallGraph;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Component(role = ProjectDependencyAnalyzer.class)
public class DefaultProjectDependencyAnalyzer implements ProjectDependencyAnalyzer {

   public static final String SEPARATOR = "-------------------------------------------------------";

   /**
    * ClassAnalyzer
    */
   @Requirement
   private final ClassAnalyzer classAnalyzer = new DefaultClassAnalyzer();

   /**
    * DependencyAnalyzer
    */
   @Requirement
   private final DependencyAnalyzer dependencyAnalyzer = new ASMDependencyAnalyzer();

   private final Map<Artifact, Set<String>> artifactUsedClassesMap = new HashMap<>();
   /**
    * A map [dependency] -> [dependency classes].
    */
   private Map<Artifact, Set<String>> artifactClassesMap = new HashMap<>();

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
         artifactClassesMap = buildArtifactClassMap(project);

         // direct dependencies of the project
         System.out.println(SEPARATOR);
         Set<Artifact> declaredArtifacts = project.getDependencyArtifacts();
         System.out.println("DIRECT DEPENDENCIES: " + declaredArtifacts);

         // transitive dependencies of the project
         System.out.println(SEPARATOR);
         Set<Artifact> transitiveArtifacts = removeAll(project.getArtifacts(), declaredArtifacts);
         System.out.println("TRANSITIVE DEPENDENCIES: " + transitiveArtifacts);

         /* ******************** bytecode analysis ********************* */

         // set of classes in project
         Set<String> builtProjectDependencyClasses = buildProjectDependencyClasses(project);
         Set<String> projectClasses = new HashSet<>(DefaultCallGraph.getProjectVertices());

         // System.out.println("PROJECT CLASSES: " + projectClasses);
         // System.out.println("Number of vertices before: " + DefaultCallGraph.getVertices().size());
         // Set<String> builtDependenciesDependencyClasses = buildDependenciesDependencyClasses(project);
         // Set<String> dependencyClasses = DefaultCallGraph.getProjectVertices();
         // dependencyClasses.removeAll(projectClasses);
         // System.out.println("DEPENDENCY CLASSES: " + dependencyClasses);
         // System.out.println("Number of vertices after: " + DefaultCallGraph.getVertices().size());

         /* ******************** usage analysis ********************* */

         // System.out.println("PROJECT CLASSES: " + projectClasses);
         // search for the dependencies used by the project
         Set<String> referencedClasses = DefaultCallGraph.referencedClassMembers(projectClasses);
         Set<Artifact> usedArtifacts = collectUsedArtifacts(artifactClassesMap, referencedClasses);

         /* ******************** call graph analysis ******************** */
         System.out.println(SEPARATOR);
         System.out.println("USED DEPENDENCIES: " + usedArtifacts);
         System.out.println(SEPARATOR);

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

   private Set<String> buildProjectDependencyClasses(MavenProject project) throws IOException {
      Set<String> dependencyClasses = new HashSet<>();
      /* paths to project compiled classes */
      String outputDirectory = project.getBuild().getOutputDirectory();
      String testOutputDirectory = project.getBuild().getTestOutputDirectory();
      /* construct the dependency classes */
      dependencyClasses.addAll(collectDependencyClasses(outputDirectory));
      dependencyClasses.addAll(collectDependencyClasses(testOutputDirectory));
      return dependencyClasses;
   }

   private Set<String> buildDependenciesDependencyClasses(MavenProject project) throws IOException {
      Set<String> dependencyClasses = new HashSet<>();
      String dependenciesDirectory = project.getBuild().getDirectory() + "/" + "dependency";
      dependencyClasses.addAll(collectDependencyClasses(dependenciesDirectory));
      return dependencyClasses;
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

   protected Set<Artifact> buildDeclaredArtifacts(MavenProject project) {
      Set<Artifact> declaredArtifacts = project.getArtifacts();
      if (declaredArtifacts == null) {
         declaredArtifacts = Collections.emptySet();
      }
      return declaredArtifacts;
   }

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

