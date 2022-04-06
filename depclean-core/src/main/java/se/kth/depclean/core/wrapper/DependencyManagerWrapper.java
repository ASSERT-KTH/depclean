package se.kth.depclean.core.wrapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import se.kth.depclean.core.AbstractDebloater;
import se.kth.depclean.core.analysis.graph.DependencyGraph;
import se.kth.depclean.core.analysis.model.ProjectDependencyAnalysis;

/**
 * Tells a dependency manager (i.e. Maven, Gradle, ...) what to expose so the process can be managed
 * from the core rather than from the dependency manager plugin.
 */
public interface DependencyManagerWrapper {

  /**
   * The dependency manager logger.
   *
   * @return The dependency manager logger
   */
  LogWrapper getLog();

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
   * A representation of the dependency manager's dependency graph.
   *
   * @return the graph
   */
  DependencyGraph dependencyGraph();

  /**
   * Where the sources are. Default is src/main/java.
   *
   * @return the graph
   */
  Path getSourceDirectory();

  /**
   * Where the tests sources are. Default is src/main/java.
   *
   * @return the graph
   */
  Path getTestDirectory();

  /**
   * Where the sources are compiled to.
   *
   * @return the paths to the compiled sources folders
   */
  Set<Path> getOutputDirectories();

  /**
   * Where the tests sources are compiled to.
   *
   * @return the paths to the compiled test sources folders
   */
  Set<Path> getTestOutputDirectories();

  /**
   * Find classes used in processors.
   *
   * @return the processors
   */
  Set<String> collectUsedClassesFromProcessors();

  /**
   * Where the compiled dependencies are located.
   *
   * @return the path to the compiled dependencies.
   */
  Path getDependenciesDirectory();

  /**
   * Find classes used in sources.
   *
   * @return the classes used.
   */
  Set<String> collectUsedClassesFromSource(Path sourceDirectory, Path testDirectory);

  /**
   * The instance that will debloat the config file.
   *
   * @param analysis the depclean analysis
   * @return the debloater
   */
  AbstractDebloater<?> getDebloater(ProjectDependencyAnalysis analysis);

  /**
   * The build directory path.
   *
   * @return the build directory path
   */
  Path getBuildDirectory();

  /**
   * Generates the dependency tree.
   *
   * @param treeFile the file to store the result to
   */
  void generateDependencyTree(File treeFile) throws IOException, InterruptedException;

  /**
   * Gets the JSON representation of the dependency tree.
   *
   * @param treeFile           the file containing the tree
   * @param analysis           the depclean analysis result
   * @param classUsageFile     the class usage file
   * @param createCallGraphCsv whether to write the call graph of usages down
   * @return the JSON tree
   */
  String getTreeAsJson(File treeFile, ProjectDependencyAnalysis analysis, File classUsageFile, boolean createCallGraphCsv);
}