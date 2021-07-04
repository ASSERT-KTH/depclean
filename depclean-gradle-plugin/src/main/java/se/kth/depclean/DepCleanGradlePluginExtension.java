package se.kth.depclean;

import java.util.List;
import java.util.Set;

public class DepCleanGradlePluginExtension {

  /**
   * If this is true, DepClean will not analyze the test sources in the project, and, therefore, the dependencies that
   * are only used for testing will be considered unused. This property is useful to detect dependencies that have a
   * compile scope but are only used during testing. Hence, these dependencies should have a test scope.
   */
  public boolean IgnoreTest = false;

  /**
   * If this is true, and DepClean reported any unused direct dependency in the dependency tree, then the project's
   * build fails immediately after running DepClean.
   */
  public boolean failIfUnusedDirect = false;

  /**
   * If this is true, and DepClean reported any unused transitive dependency in the dependency tree, then the project's
   * build fails immediately after running DepClean.
   */
  public boolean failIfUnusedTransitive = false;

  /**
   * If this is true, and DepClean reported any unused inherited dependency in the dependency tree, then the project's
   * build fails immediately after running DepClean.
   */
  public boolean failIfUnusedInherited = false;

  /**
   * Ignore dependencies with specific configurations from the DepClean analysis.
   */
  public List<String> ignoreConfiguration;

  /**
   * Add a list of dependencies, identified by their coordinates, to be ignored by DepClean during the analysis and
   * considered as used dependencies. Useful to override incomplete result caused by bytecode-level analysis Dependency
   * format is <code>groupId:artifactId:version</code>.
   */
  public Set<String> ignoreDependency;

  public boolean isIgnoreTest() {
    return IgnoreTest;
  }

  public boolean isFailIfUnusedDirect() {
    return failIfUnusedDirect;
  }

  public boolean isFailIfUnusedTransitive() {
    return failIfUnusedTransitive;
  }

  public boolean isFailIfUnusedInherited() {
    return failIfUnusedInherited;
  }

  public List<String> getIgnoreConfiguration() { return ignoreConfiguration; }

  public Set<String> getIgnoreDependency() { return ignoreDependency; }

}
