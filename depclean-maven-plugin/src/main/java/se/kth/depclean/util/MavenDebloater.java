package se.kth.depclean.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.project.MavenProject;
import se.kth.depclean.core.AbstractDebloater;
import se.kth.depclean.core.analysis.model.DebloatedDependency;
import se.kth.depclean.core.analysis.model.ProjectDependencyAnalysis;

/**
 * Writes a debloated pom is needed.
 */
@Slf4j
public class MavenDebloater extends AbstractDebloater<Dependency> {

  private final MavenProject project;
  private final Model model;
  private final List<Dependency> initialDependencies;

  /**
   * Creates the debloater.
   *
   * @param analysis the depclean analysis result
   * @param project the maven project
   * @param model the maven model
   */
  public MavenDebloater(ProjectDependencyAnalysis analysis, MavenProject project, Model model) {
    super(analysis);
    this.project = project;
    this.model = model;
    this.initialDependencies = model.getDependencies();
  }

  @Override
  protected void logDependencies() {
    model.getDependencies().forEach(dep -> {
      log.debug("Debloated dependency {}", dep);
      dep.getExclusions().forEach(excl -> log.debug("- Excluding {}:{}",
          excl.getGroupId(), excl.getArtifactId()));
    });
  }

  @Override
  protected Dependency toMavenDependency(DebloatedDependency debloatedDependency) {
    final Dependency dependency = createDependency(debloatedDependency);
    debloatedDependency.getExclusions().forEach(depToExclude -> exclude(dependency, depToExclude));
    return dependency;
  }

  @Override
  protected void setDependencies(List<Dependency> dependencies) {
    model.setDependencies(dependencies);
  }

  private void exclude(Dependency dependency, se.kth.depclean.core.model.Dependency dependencyToExclude) {
    Exclusion exclusion = new Exclusion();
    exclusion.setGroupId(dependencyToExclude.getGroupId());
    exclusion.setArtifactId(dependencyToExclude.getDependencyId());
    dependency.addExclusion(exclusion);
  }

  @Override
  protected void postProcessDependencies() {
    model.getDependencies().forEach(dep -> {
      for (Dependency initialDependency : initialDependencies) {
        if (hasVersionAsProperty(initialDependency) && matches(dep, initialDependency)) {
          dep.setVersion(initialDependency.getVersion());
        }
      }
    });
  }

  @Override
  protected void writeFile() throws IOException {
    String pathToDebloatedPom =
        project.getBasedir().getAbsolutePath() + File.separator + "pom-debloated.xml";
    Path path = Paths.get(pathToDebloatedPom);
    writePom(path);
    log.info("POM debloated successfully");
    log.info("pom-debloated.xml file created in: " + pathToDebloatedPom);
  }

  private boolean hasVersionAsProperty(Dependency initialDependency) {
    return initialDependency.getVersion().startsWith("$");
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

  private Artifact findArtifact(se.kth.depclean.core.model.Dependency dependency) {
    return project.getArtifacts().stream()
        .filter(artifact -> matches(artifact, dependency))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Unable to find " + dependency + " in dependencies"));
  }

  private boolean matches(Artifact artifact, se.kth.depclean.core.model.Dependency coordinate) {
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
   * se.kth.depclean.core.model.Dependency}.
   *
   * @param dependency The depclean dependency to create the maven dependency.
   * @return The Dependency object.
   */
  private Dependency createDependency(
      final se.kth.depclean.core.model.Dependency dependency) {
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
