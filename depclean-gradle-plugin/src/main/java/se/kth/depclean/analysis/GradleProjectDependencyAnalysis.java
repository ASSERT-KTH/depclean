package se.kth.depclean.analysis;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.component.Artifact;

public class GradleProjectDependencyAnalysis {

  /**
   * Store all the artifacts
   */
  private final Set<ResolvedArtifact> allArtifacts;

  /**
   * Store all the used declared artifacts (ie. used direct dependencies).
   */
  private final Set<ResolvedArtifact> usedDeclaredArtifacts;

  /**
   * Store all the used undeclared artifacts (ie. used transitive dependencies).
   */
  private final Set<ResolvedArtifact> usedUndeclaredArtifacts;

  /**
   * Store all the unused declared artifacts (ie. unused transitive dependencies).
   */
  private final Set<ResolvedArtifact> unusedDeclaredArtifacts;

  /**
   * Ctor.
   */
  public GradleProjectDependencyAnalysis(
          Set<ResolvedArtifact> allArtifacts,
          Set<ResolvedArtifact> usedDeclaredArtifacts,
          Set<ResolvedArtifact> usedUndeclaredArtifacts,
          Set<ResolvedArtifact> unusedDeclaredArtifacts) {
    this.allArtifacts = safeCopy(allArtifacts);
    this.usedDeclaredArtifacts = safeCopy(usedDeclaredArtifacts);
    this.usedUndeclaredArtifacts = safeCopy(usedUndeclaredArtifacts);
    this.unusedDeclaredArtifacts = safeCopy(unusedDeclaredArtifacts);
  }

  /**
   * To prevent unnecessary and unexpected modification in the set.
   *
   * @param set required set.
   * @return An unmodifiable set corresponding to the provided set.
   */
  private Set<ResolvedArtifact> safeCopy(Set<ResolvedArtifact> set) {
    return (set == null) ? Collections.emptySet()
        : Collections.unmodifiableSet(new LinkedHashSet<ResolvedArtifact >(set));
  }

//    /**
//   * Filter out artifacts with scope other than compile from the set of unused declared artifacts.
//   *
//   * @return updated project dependency analysis
//   * @since 1.3
//   */
//  public GradleProjectDependencyAnalysis ignoreNonCompile() {
//    Set<ResolvedArtifact> filteredUnusedDeclared = new HashSet<>(unusedDeclaredArtifacts);
////    filteredUnusedDeclared.removeIf(resolvedArtifact -> !resolvedArtifact.getScope().equals(Artifact.SCOPE_COMPILE));
//    filteredUnusedDeclared.removeIf(resolvedArtifact -> !resolvedArtifact.get);
//    return new GradleProjectDependencyAnalysis(usedDeclaredArtifacts, usedUndeclaredArtifacts, filteredUnusedDeclared);
//  }

  /**
   * Overrides the hash code value method of the object.
   */
  @Override
  public int hashCode() {
    int hashCode = getAllArtifacts().hashCode();
    hashCode = (hashCode * 37) + getUsedDeclaredArtifacts().hashCode();
    hashCode = (hashCode * 37) + getUsedUndeclaredArtifacts().hashCode();
    hashCode = (hashCode * 37) + getUnusedDeclaredArtifacts().hashCode();

    return hashCode;
  }

  /**
   * all artifacts.
   *
   * @return {@link Artifact}
   */
  public Set<ResolvedArtifact> getAllArtifacts() { return allArtifacts; }

  /**
   * Used declared artifacts.
   *
   * @return {@link Artifact}
   */
  public Set<ResolvedArtifact> getUsedDeclaredArtifacts() { return usedDeclaredArtifacts;   }

  // Object methods ---------------------------------------------------------

  /**
   * Used but not declared artifacts.
   *
   * @return {@link Artifact}
   */
  public Set<ResolvedArtifact> getUsedUndeclaredArtifacts() {
    return usedUndeclaredArtifacts;
  }

  /**
   * Unused but declared artifacts.
   *
   * @return {@link Artifact}
   */
  public Set<ResolvedArtifact> getUnusedDeclaredArtifacts() {
    return unusedDeclaredArtifacts;
  }


  /**
   * Overrides the standard equals method of Object.
   */
  @Override
  public boolean equals(Object object) {
    if (object instanceof GradleProjectDependencyAnalysis) {
      GradleProjectDependencyAnalysis analysis = (GradleProjectDependencyAnalysis) object;
      return getAllArtifacts().equals(analysis.getAllArtifacts())
          && getUsedDeclaredArtifacts().equals(analysis.getUsedDeclaredArtifacts())
          && getUsedUndeclaredArtifacts().equals(analysis.getUsedUndeclaredArtifacts())
          && getUnusedDeclaredArtifacts().equals(analysis.getUnusedDeclaredArtifacts());
    }

    return false;
  }

  /**
   * Overrides de toString standard method of class Object @see java.lang.Object#toString().
   */
  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();

    if (!getAllArtifacts().isEmpty()) {
      buffer.append("allArtifacts=").append(getAllArtifacts());
    }

    if (!getUsedDeclaredArtifacts().isEmpty()) {
      if(buffer.length() > 0) {
        buffer.append(",");
      }
      buffer.append("usedDeclaredArtifacts=").append(getUsedDeclaredArtifacts());
    }

    if (!getUsedUndeclaredArtifacts().isEmpty()) {
      if (buffer.length() > 0) {
        buffer.append(",");
      }
      buffer.append("usedUndeclaredArtifacts=").append(getUsedUndeclaredArtifacts());
    }

    if (!getUnusedDeclaredArtifacts().isEmpty()) {
      if (buffer.length() > 0) {
        buffer.append(",");
      }
      buffer.append("unusedDeclaredArtifacts=").append(getUnusedDeclaredArtifacts());
    }

    buffer.insert(0, "[");
    buffer.insert(0, getClass().getName());

    buffer.append("]");

    return buffer.toString();
  }
}
