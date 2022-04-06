package se.kth.depclean.analysis;

import org.gradle.api.Project;

public interface GradleProjectDependencyAnalyzer {

  // fields -----------------------------------------------------------------
  String ROLE = GradleProjectDependencyAnalyzer.class.getName();

  // public methods ---------------------------------------------------------
  GradleProjectDependencyAnalysis analyze(Project project);
}

