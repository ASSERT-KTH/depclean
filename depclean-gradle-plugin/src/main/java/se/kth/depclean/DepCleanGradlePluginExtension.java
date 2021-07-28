package se.kth.depclean;

import java.util.Set;
import org.gradle.api.Project;

/**
 * This extension class allows you to add optional parameters to the default (debloat) task.
 */
public class DepCleanGradlePluginExtension {

  /**
   * The Gradle project to analyze.
   */
  public Project project = null;

  /**
   * Skip plugin execution completely.
   */
  public boolean skipDepClean = false;

  /**
   * If this is true, DepClean will not analyze the test sources in the project, and, therefore,
   * the dependencies that are only used for testing will be considered unused. This property is
   * useful to detect dependencies that have a compile scope but are only used during testing.
   * Hence, these dependencies should have a test scope.
   */
  public boolean ignoreTest = false;

  /**
   * If this is true, and DepClean reported any unused direct dependency in the dependency tree,
   * then the project's build fails immediately after running DepClean.
   */
  public boolean failIfUnusedDirect = false;

  /**
   * If this is true, and DepClean reported any unused transitive dependency in the dependency tree,
   * then the project's build fails immediately after running DepClean.
   */
  public boolean failIfUnusedTransitive = false;

  /**
   * If this is true, and DepClean reported any unused inherited dependency in the dependency tree,
   * then the project's build fails immediately after running DepClean.
   */
  public boolean failIfUnusedInherited = false;

  /**
   * Ignore dependencies with specific configurations from the DepClean analysis.
   */
  public Set<String> ignoreConfiguration;

  /**
   * Add a list of dependencies, identified by their coordinates, to be ignored by DepClean during
   * the analysis and considered as used dependencies. Useful to override incomplete result caused
   * by bytecode-level analysis Dependency format is <code>groupId:artifactId:version</code>.
   */
  public Set<String> ignoreDependency;

  // Getters ==========================================

  public Project getProject() {
    return project;
  }

  public boolean isSkipDepClean() {
    return skipDepClean;
  }

  public boolean isIgnoreTest() {
    return ignoreTest;
  }

  public boolean isFailIfUnusedDirect() { return failIfUnusedDirect; }

  public boolean isFailIfUnusedTransitive() {
    return failIfUnusedTransitive;
  }

  public boolean isFailIfUnusedInherited() {
    return failIfUnusedInherited;
  }

  public Set<String> getIgnoreConfiguration() {
    return ignoreConfiguration;
  }

  public Set<String> getIgnoreDependency() {
    return ignoreDependency;
  }
}
