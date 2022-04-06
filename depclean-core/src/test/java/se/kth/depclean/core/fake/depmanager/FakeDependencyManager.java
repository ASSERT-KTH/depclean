package se.kth.depclean.core.fake.depmanager;

import static com.google.common.collect.ImmutableSet.of;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import se.kth.depclean.core.AbstractDebloater;
import se.kth.depclean.core.analysis.graph.DependencyGraph;
import se.kth.depclean.core.analysis.model.ProjectDependencyAnalysis;
import se.kth.depclean.core.fake.FakeDependencyGraph;
import se.kth.depclean.core.wrapper.DependencyManagerWrapper;
import se.kth.depclean.core.wrapper.LogWrapper;

public class FakeDependencyManager implements DependencyManagerWrapper {

  protected Path RESOURCES_PATH = Paths.get("src/test/resources");
  protected Path END_2_END_PATH = RESOURCES_PATH.resolve("end2end");

  private final Logger log;
  private final FakeDependencyGraph dependencyGraph;

  public FakeDependencyManager(Logger log) {
    this(log, new FakeDependencyGraph() {
    });
  }

  public FakeDependencyManager(Logger log, FakeDependencyGraph dependencyGraph) {
    this.log = log;
    this.dependencyGraph = dependencyGraph;
  }

  @Override
  public LogWrapper getLog() {
    return new LogWrapper() {
      @Override
      public void info(String message) {
        log.info(message);
      }

      @Override
      public void error(String message) {
        log.error(message);
      }

      @Override
      public void debug(String message) {
        log.debug(message);
      }
    };
  }

  @Override
  public boolean isMaven() {
    return true;
  }

  @Override
  public boolean isPackagingPom() {
    return false;
  }

  @Override
  public DependencyGraph dependencyGraph() {
    return dependencyGraph;
  }

  @Override
  public Path getSourceDirectory() {
    return FileUtils.getTempDirectory().toPath();
  }

  @Override
  public Path getTestDirectory() {
    return FileUtils.getTempDirectory().toPath();
  }

  @Override
  public Set<Path> getOutputDirectories() {
    return of();
  }

  @Override
  public Set<Path> getTestOutputDirectories() {
    return of();
  }

  @Override
  public Set<String> collectUsedClassesFromProcessors() {
    return of();
  }

  @Override
  public Path getDependenciesDirectory() {
    return FileUtils.getTempDirectory().toPath();
  }

  @Override
  public Set<String> collectUsedClassesFromSource(Path sourceDirectory, Path testDirectory) {
    return of();
  }

  @Override
  public AbstractDebloater<? extends Serializable> getDebloater(ProjectDependencyAnalysis analysis) {
    return null;
  }

  @Override
  public Path getBuildDirectory() {
    return FileUtils.getTempDirectory().toPath();
  }

  @Override
  public void generateDependencyTree(File treeFile) {

  }

  @Override
  public String getTreeAsJson(File treeFile, ProjectDependencyAnalysis analysis, File classUsageFile,
                              boolean createClassUsageCsv) {
    return null;
  }
}
