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

  // Getters ==========================================

  public Project getProject() {
    return project;
  }

  public boolean isSkipDepClean() {
    return skipDepClean;
  }

}
