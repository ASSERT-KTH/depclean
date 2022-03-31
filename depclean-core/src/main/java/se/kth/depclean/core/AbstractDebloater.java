package se.kth.depclean.core;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import se.kth.depclean.core.analysis.model.DebloatedDependency;
import se.kth.depclean.core.analysis.model.ProjectDependencyAnalysis;

/**
 * Analyses the analysis result and writes the debloated config file.
 */
@Slf4j
@AllArgsConstructor
public abstract class AbstractDebloater<T> {

  protected final ProjectDependencyAnalysis analysis;

  /**
   * Writes the debloated config file down.
   */
  public void write() throws IOException {
    log.info("Starting debloating file");
    logChanges();
    setDependencies(analysis.getDebloatedDependencies().stream()
        .map(this::toMavenDependency)
        .collect(Collectors.toList()));

    if (log.isDebugEnabled()) {
      logDependencies();
    }
    postProcessDependencies();
    writeFile();
  }

  protected abstract T toMavenDependency(DebloatedDependency debloatedDependency);

  protected abstract void setDependencies(List<T> dependencies);

  protected abstract void writeFile() throws IOException;

  protected abstract void logDependencies();

  /**
   * In order to keep the version as variable (property) for dependencies that were declared as such, post-process
   * dependencies to replace interpolated version with the initial one.
   */
  protected abstract void postProcessDependencies();

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
}
