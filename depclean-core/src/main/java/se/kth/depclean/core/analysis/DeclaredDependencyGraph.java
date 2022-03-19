package se.kth.depclean.core.analysis;

import static com.google.common.collect.ImmutableSet.copyOf;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.artifact.Artifact;

/**
 * Recursively calculates the dependencies from the given artifacts.
 */
@Slf4j
public final class DeclaredDependencyGraph {

  private final Multimap<Artifact, String> artifactAndClasses = ArrayListMultimap.create();
  private final Multimap<String, Artifact> classesAndArtifacts = ArrayListMultimap.create();
  private final Set<Artifact> directDependencyArtifacts;

  DeclaredDependencyGraph(Set<Artifact> allArtifacts, Set<Artifact> directDependencyArtifacts) throws IOException {
    this.directDependencyArtifacts = directDependencyArtifacts;

    for (Artifact artifact : allArtifacts) {
      File file = artifact.getFile();
      if (file != null && file.getName().endsWith(".jar")) {
        // optimized solution for the jar case
        try (JarFile jarFile = new JarFile(file)) {
          Enumeration<JarEntry> jarEntries = jarFile.entries();
          while (jarEntries.hasMoreElements()) {
            String entry = jarEntries.nextElement().getName();
            if (entry.endsWith(".class")) {
              String className = entry.replace('/', '.');
              className = className.substring(0, className.length() - ".class".length());
              artifactAndClasses.put(artifact, className);
            }
          }
        }
      } else if (file != null && file.isDirectory()) {
        URL url = file.toURI().toURL();
        ClassAnalyzer classAnalyzer = new DefaultClassAnalyzer();
        Set<String> classes = classAnalyzer.analyze(url);
        classes.forEach(c -> artifactAndClasses.put(artifact, c));
      }
    }
    Multimaps.invertFrom(artifactAndClasses, classesAndArtifacts);

    log.info("# All artifacts");
    artifactAndClasses.keySet().forEach(artifact -> log.info("## Found artifact {}", artifact));
    log.info("# Direct artifacts");
    directDependencyArtifacts.forEach(artifact -> log.info("## Found artifact {}", artifact));
  }

  public Set<String> getClassesForArtifact(Artifact artifact) {
    return copyOf(artifactAndClasses.get(artifact));
  }

  public Set<Artifact> getArtifactsForClass(String clazz) {
    return copyOf(classesAndArtifacts.get(clazz));
  }

  public Set<Artifact> getDirectDependencyArtifacts() {
    return copyOf(directDependencyArtifacts);
  }

  public Set<Artifact> getAllArtifacts() {
    return copyOf(artifactAndClasses.keySet());
  }

  public boolean doesntKnow(String className) {
    return getArtifactsForClass(className).isEmpty();
  }
}
