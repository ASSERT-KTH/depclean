package se.kth.depclean;

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
}
