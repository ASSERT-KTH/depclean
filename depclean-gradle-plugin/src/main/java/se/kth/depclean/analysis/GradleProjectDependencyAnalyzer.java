package se.kth.depclean.analysis;

import org.gradle.api.Project;
import se.kth.depclean.core.analysis.ProjectDependencyAnalyzerException;

public interface GradleProjectDependencyAnalyzer {

  // fields -----------------------------------------------------------------
  String ROLE = GradleProjectDependencyAnalyzer.class.getName();

  // public methods ---------------------------------------------------------
  GradleProjectDependencyAnalysis analyze(Project project) throws ProjectDependencyAnalyzerException;
}

