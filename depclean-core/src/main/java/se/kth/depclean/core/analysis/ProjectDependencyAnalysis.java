package se.kth.depclean.core.analysis;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.apache.maven.artifact.Artifact;

/**
 * Project dependencies analysis result.
 */
public class ProjectDependencyAnalysis {

  /**
   * Store all the used declared artifacts (ie. used direct dependencies).
   */
  private final Set<Artifact> usedDirectArtifacts;

  /**
   * Store all the used undeclared artifacts (ie. used transitive dependencies).
   */
  private final Set<Artifact> usedTransitiveArtifacts;

  /**
   * Store all the unused declared artifacts (ie. unused direct dependencies).
   */
  private final Set<Artifact> unusedDirectArtifacts;
  /**
   * Store all the known artifact with their classes and their used classes.
   */
  private final Map<String, ArtifactTypes> artifactClassesMap;

  /**
   * Ctor.
   */
  public ProjectDependencyAnalysis(
      Set<Artifact> usedDirectArtifacts,
      Set<Artifact> usedTransitiveArtifacts,
      Set<Artifact> unusedDirectArtifacts,
      Map<String, ArtifactTypes> artifactClassesMap) {
    this.usedDirectArtifacts = safeCopy(usedDirectArtifacts);
    this.usedTransitiveArtifacts = safeCopy(usedTransitiveArtifacts);
    this.unusedDirectArtifacts = safeCopy(unusedDirectArtifacts);
    this.artifactClassesMap = artifactClassesMap;
  }

  /**
   * To prevent unnecessary and unexpected modification in the set.
   *
   * @param set The required set.
   * @return An unmodifiable set corresponding to the provided set.
   */
  private Set<Artifact> safeCopy(Set<Artifact> set) {
    return (set == null) ? Collections.emptySet()
        : Collections.unmodifiableSet(new LinkedHashSet<>(set));
  }

  /**
   * Overrides the hash code value method of the object.
   */
  @Override
  public int hashCode() {
    int hashCode = getUsedDirectArtifacts().hashCode();
    hashCode = (hashCode * 37) + getUsedTransitiveArtifacts().hashCode();
    hashCode = (hashCode * 37) + getUnusedDirectArtifacts().hashCode();
    return hashCode;
  }

  /**
   * Used declared artifacts.
   *
   * @return {@link Artifact}
   */
  public Set<Artifact> getUsedDirectArtifacts() {
    return usedDirectArtifacts;
  }

  // Object methods ---------------------------------------------------------

  /**
   * Used but not declared artifacts.
   *
   * @return {@link Artifact}
   */
  public Set<Artifact> getUsedTransitiveArtifacts() {
    return usedTransitiveArtifacts;
  }

  /**
   * Unused but declared artifacts.
   *
   * @return {@link Artifact}
   */
  public Set<Artifact> getUnusedDirectArtifacts() {
    return unusedDirectArtifacts;
  }

  /**
   * Artifacts with their classes and used classes.
   *
   * @return the artifact map
   */
  public Map<String, ArtifactTypes> getArtifactClassesMap() {
    return artifactClassesMap;
  }

  /**
   * Overrides the standard equals method of Object.
   */
  @Override
  public boolean equals(Object object) {
    if (object instanceof ProjectDependencyAnalysis) {
      ProjectDependencyAnalysis analysis = (ProjectDependencyAnalysis) object;
      return getUsedDirectArtifacts().equals(analysis.getUsedDirectArtifacts())
          && getUsedTransitiveArtifacts().equals(analysis.getUsedTransitiveArtifacts())
          && getUnusedDirectArtifacts().equals(analysis.getUnusedDirectArtifacts());
    }

    return false;
  }

  /**
   * Overrides de toString standard method of class Object @see java.lang.Object#toString().
   */
  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();

    if (!getUsedDirectArtifacts().isEmpty()) {
      buffer.append("usedDeclaredArtifacts=").append(getUsedDirectArtifacts());
    }

    if (!getUsedTransitiveArtifacts().isEmpty()) {
      if (buffer.length() > 0) {
        buffer.append(",");
      }
      buffer.append("usedUndeclaredArtifacts=").append(getUsedTransitiveArtifacts());
    }

    if (!getUnusedDirectArtifacts().isEmpty()) {
      if (buffer.length() > 0) {
        buffer.append(",");
      }
      buffer.append("unusedDeclaredArtifacts=").append(getUnusedDirectArtifacts());
    }

    buffer.insert(0, "[");
    buffer.insert(0, getClass().getName());

    buffer.append("]");

    return buffer.toString();
  }
}
