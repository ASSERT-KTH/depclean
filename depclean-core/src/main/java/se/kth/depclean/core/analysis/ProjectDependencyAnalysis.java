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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.maven.artifact.Artifact;

/**
 * Project dependencies analysis result.
 */
public class ProjectDependencyAnalysis {

  /**
   * Store all the usedDeclaredArtifacts.
   */
  private final Set<Artifact> usedDeclaredArtifacts;

  /**
   * Store all the usedUndeclaredArtifacts.
   */
  private final Set<Artifact> usedUndeclaredArtifacts;

  /**
   * Store all the unusedDeclaredArtifacts.
   */
  private final Set<Artifact> unusedDeclaredArtifacts;

  /**
   * Ctor.
   */
  public ProjectDependencyAnalysis(Set<Artifact> usedDeclaredArtifacts,
      Set<Artifact> usedUndeclaredArtifacts,
      Set<Artifact> unusedDeclaredArtifacts) {
    this.usedDeclaredArtifacts = safeCopy(usedDeclaredArtifacts);
    this.usedUndeclaredArtifacts = safeCopy(usedUndeclaredArtifacts);
    this.unusedDeclaredArtifacts = safeCopy(unusedDeclaredArtifacts);
  }

  /**
   * To prevent unnecessary and unexpected modification in the set.
   * @param set Required set.
   * @return an unmodifiable set corresponding to the provided set.
   */
  private Set<Artifact> safeCopy(Set<Artifact> set) {
    return (set == null) ? Collections.emptySet()
        : Collections.unmodifiableSet(new LinkedHashSet<Artifact>(set));
  }

  /**
   * Filter not-compile scoped artifacts from unused declared.
   *
   * @return updated project dependency analysis
   * @since 1.3
   */
  public ProjectDependencyAnalysis ignoreNonCompile() {
    Set<Artifact> filteredUnusedDeclared = new HashSet<>(unusedDeclaredArtifacts);
    // This loop will iterate over all the elements of the set and remove those elements whose scope is not compile.
    filteredUnusedDeclared.removeIf(artifact -> !artifact.getScope().equals(Artifact.SCOPE_COMPILE));
    return new ProjectDependencyAnalysis(usedDeclaredArtifacts, usedUndeclaredArtifacts, filteredUnusedDeclared);
  }

  /**
   * Overrides the hash code value method of the object.
   */
  @Override
  public int hashCode() {
    int hashCode = getUsedDeclaredArtifacts().hashCode();
    hashCode = (hashCode * 37) + getUsedUndeclaredArtifacts().hashCode();
    hashCode = (hashCode * 37) + getUnusedDeclaredArtifacts().hashCode();
    return hashCode;
  }

  /**
   * Used and declared artifacts.
   *
   * @return {@link Artifact}
   */
  public Set<Artifact> getUsedDeclaredArtifacts() {
    return usedDeclaredArtifacts;
  }

  // Object methods ---------------------------------------------------------

  /**
   * Used but not declared artifacts.
   *
   * @return {@link Artifact}
   */
  public Set<Artifact> getUsedUndeclaredArtifacts() {
    return usedUndeclaredArtifacts;
  }

  /**
   * Unused but declared artifacts.
   *
   * @return {@link Artifact}
   */
  public Set<Artifact> getUnusedDeclaredArtifacts() {
    return unusedDeclaredArtifacts;
  }

  /**
   * Overrides the standard equals method of Object.
   */
  @Override
  public boolean equals(Object object) {
    if (object instanceof ProjectDependencyAnalysis) {
      ProjectDependencyAnalysis analysis = (ProjectDependencyAnalysis) object;
      return getUsedDeclaredArtifacts().equals(analysis.getUsedDeclaredArtifacts())
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

    if (!getUsedDeclaredArtifacts().isEmpty()) {
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
