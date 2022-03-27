package se.kth.depclean.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import se.kth.depclean.core.analysis.ProjectDependencyAnalysis;
import se.kth.depclean.core.analysis.model.DebloatedDependency;

/**
 * Writes a debloated pom is needed.
 */
@Slf4j
@AllArgsConstructor
public class DebloatedPomWriter {

  private final MavenProject project;
  private final Model model;
  private final ProjectDependencyAnalysis analysis;

  /**
   * Generates the debloated pom.
   *
   * @throws MojoExecutionException if file can't be written
   */
  public void write() throws MojoExecutionException {
    log.info("Starting debloating POM");
    logChanges();
    final List<Dependency> initialDependencies = model.getDependencies();
    model.setDependencies(analysis.getDebloatedDependencies().stream()
        .map(this::toMavenDependency)
        .collect(Collectors.toList()));

    if (log.isDebugEnabled()) {
      model.getDependencies().forEach(dep -> {
        log.debug("Debloated dependency {}", dep);
        dep.getExclusions().forEach(excl -> log.debug("- Excluding {}:{}",
            excl.getGroupId(), excl.getArtifactId()));
      });
    }

    postProcessDependencies(initialDependencies);
    writeDebloatedPom();
  }

  private void logChanges() {
    if (analysis.hasUsedTransitiveDependencies()) {
      final int dependencyAmount = analysis.getUsedTransitiveDependencies().size();
      log.info("Adding {} used transitive {} as direct {}.",
          dependencyAmount, getDependencyWording(dependencyAmount), getDependencyWording(dependencyAmount));
    }

    if (analysis.hasUnusedDirectDependencies()) {
      final int dependencyAmount = analysis.getUnusedDirectDependencies().size();
      log.info("Removing {} unused direct {}.", dependencyAmount, getDependencyWording(dependencyAmount));
    }

    if (analysis.hasUnusedTransitiveDependencies()) {
      final int dependencyAmount = analysis.getUnusedTransitiveDependencies().size();
      log.info(
          "Excluding {} unused transitive {} one-by-one.", dependencyAmount, getDependencyWording(dependencyAmount));
    }
  }

  private String getDependencyWording(int amount) {
    return amount > 1 ? "dependencies" : "dependency";
  }

  private Dependency toMavenDependency(DebloatedDependency debloatedDependency) {
    final Dependency dependency = createDependency(debloatedDependency);
    debloatedDependency.getExclusions().forEach(depToExclude -> exclude(dependency, depToExclude));
    return dependency;
  }

  private void exclude(Dependency dependency, se.kth.depclean.core.analysis.model.Dependency dependencyToExclude) {
    Exclusion exclusion = new Exclusion();
    exclusion.setGroupId(dependencyToExclude.getGroupId());
    exclusion.setArtifactId(dependencyToExclude.getDependencyId());
    dependency.addExclusion(exclusion);
  }

  /**
   * In order to keep the version as variable (property) for dependencies that were declared as such, post-process
   * dependencies to replace interpolated version with the initial one.
   */
  private void postProcessDependencies(List<Dependency> initialDependencies) {
    model.getDependencies().forEach(dep -> {
      for (Dependency initialDependency : initialDependencies) {
        if (hasVersionAsProperty(initialDependency) && matches(dep, initialDependency)) {
          dep.setVersion(initialDependency.getVersion());
        }
      }
    });
  }

  private boolean hasVersionAsProperty(Dependency initialDependency) {
    return initialDependency.getVersion().startsWith("$");
  }

  private void writeDebloatedPom() throws MojoExecutionException {
    String pathToDebloatedPom =
        project.getBasedir().getAbsolutePath() + File.separator + "pom-debloated.xml";
    try {
      Path path = Paths.get(pathToDebloatedPom);
      writePom(path);
    } catch (IOException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
    log.info("POM debloated successfully");
    log.info("pom-debloated.xml file created in: " + pathToDebloatedPom);
  }

  /**
   * Write pom file to the filesystem.
   *
   * @param pomFile The path to the pom.
   * @throws IOException In case of any IO issue.
   */
  private void writePom(final Path pomFile) throws IOException {
    MavenXpp3Writer writer = new MavenXpp3Writer();
    writer.write(Files.newBufferedWriter(pomFile), model);
  }

  private Artifact findArtifact(se.kth.depclean.core.analysis.model.Dependency dependency) {
    return project.getArtifacts().stream()
        .filter(artifact -> matches(artifact, dependency))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Unable to find " + dependency + " in dependencies"));
  }

  private boolean matches(Artifact artifact, se.kth.depclean.core.analysis.model.Dependency coordinate) {
    return coordinate.toString().toLowerCase().contains(
        String.format("%s:%s:%s", artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion())
            .toLowerCase());
  }

  private boolean matches(Dependency dep, Dependency initialDependency) {
    return initialDependency.getGroupId().equals(dep.getGroupId())
        && initialDependency.getArtifactId().equals(dep.getArtifactId());
  }

  /**
   * This method creates a Maven {@link Dependency} object from a depclean {@link
   * se.kth.depclean.core.analysis.model.Dependency}.
   *
   * @param dependency The depclean dependency to create the maven dependency.
   * @return The Dependency object.
   */
  private Dependency createDependency(
      final se.kth.depclean.core.analysis.model.Dependency dependency) {
    return createDependency(findArtifact(dependency));
  }

  /**
   * This method creates a {@link Dependency} object from a Maven {@link
   * org.apache.maven.artifact.Artifact}.
   *
   * @param artifact The artifact to create the dependency.
   * @return The Dependency object.
   */
  private Dependency createDependency(final Artifact artifact) {
    Dependency dependency = new Dependency();
    dependency.setGroupId(artifact.getGroupId());
    dependency.setArtifactId(artifact.getArtifactId());
    dependency.setVersion(artifact.getVersion());
    if (artifact.hasClassifier()) {
      dependency.setClassifier(artifact.getClassifier());
    }
    dependency.setOptional(artifact.isOptional());
    dependency.setScope(artifact.getScope());
    dependency.setType(artifact.getType());
    return dependency;
  }
}
