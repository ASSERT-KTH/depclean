package se.kth.depclean.core.wrapper;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.Set;
import org.apache.maven.plugin.logging.Log;
import se.kth.depclean.core.AbstractDebloater;
import se.kth.depclean.core.analysis.graph.DependencyGraph;
import se.kth.depclean.core.analysis.model.ProjectDependencyAnalysis;

/**
 * Tells a dependency manager (i.e. Maven, gradle, ...) what to expose so the process can be managed from the core
 * rather than from the dependency manager plugin
 */
public interface DependencyManagerWrapper {

  /**
   * The dependency manager logger.
   *
   * @return The dependency manager logger
   */
  Log getLog();

  /**
   * Whether this is a Maven project.
   *
   * @return {@code true} if the is a Maven project
   */
  boolean isMaven();

  /**
   * Whether the project is of 'pom' kind.
   *
   * @return {@code true} if the project is a pom project
   */
  boolean isPackagingPom();

  /**
   * Copies the dependencies to a folder, to use them later.
   */
  void copyAndExtractDependencies();

  /**
   * A representation of the dependency manager's dependency graph.
   *
   * @return the graph
   */
  DependencyGraph dependencyGraph();

  /**
   * Where the sources are compiled to.
   *
   * @return the path to the compiled sources folder
   */
  Path getOutputDirectory();

  /**
   * Where the tests sources are compiled to.
   *
   * @return the path to the compiled test sources folder
   */
  Path getTestOutputDirectory();

  /**
   * Find classes used in processors.
   *
   * @return the processors
   */
  Set<String> collectUsedClassesFromProcessors();

  /**
   * The instance that will debloat the config file.
   *
   * @param analysis the depclean analysis
   * @return the debloater
   */
  AbstractDebloater<? extends Serializable> getDebloater(ProjectDependencyAnalysis analysis);

  /**
   * The build directory path.
   *
   * @return the build directory path
   */
  String getBuildDirectory();

  /**
   * Generates the dependency tree.
   *
   * @param treeFile the file to store the result to
   */
  void generateDependencyTree(File treeFile) throws IOException, InterruptedException;

  /**
   * Gets the JSON representation of the dependency tree.
   *
   * @param treeFile the file containing the tree
   * @param analysis the depclean analysis result
   * @param classUsageFile the class usage file
   * @param createClassUsageCsv whether to write the class usage down
   * @return the JSON tree
   */
  String getTreeAsJson(
      File treeFile, ProjectDependencyAnalysis analysis, File classUsageFile, boolean createClassUsageCsv);
}