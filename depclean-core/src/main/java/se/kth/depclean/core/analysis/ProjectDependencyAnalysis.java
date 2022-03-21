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

import static com.google.common.collect.ImmutableSet.copyOf;
import static java.util.stream.Collectors.toCollection;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import se.kth.depclean.core.analysis.model.ClassName;
import se.kth.depclean.core.analysis.model.DependencyAnalysisInfo;
import se.kth.depclean.core.analysis.model.DependencyCoordinate;

/**
 * Project dependencies analysis result.
 */
@Getter
@EqualsAndHashCode
public class ProjectDependencyAnalysis {

  private final Set<DependencyCoordinate> usedDirectDependencies;
  private final Set<DependencyCoordinate> usedTransitiveDependencies;
  private final Set<DependencyCoordinate> usedInheritedDependencies;
  private final Set<DependencyCoordinate> unusedDirectDependencies;
  private final Set<DependencyCoordinate> unusedTransitiveDependencies;
  private final Set<DependencyCoordinate> unusedInheritedDependencies;
  @Getter(AccessLevel.PROTECTED)
  private final Map<DependencyCoordinate, DependencyTypes> dependencyClassesMap;

  /**
   * The analysis result.
   *
   * @param usedDirectDependencies       used direct dependencies
   * @param usedTransitiveDependencies   used transitive dependencies
   * @param usedInheritedDependencies    used inherited dependencies
   * @param unusedDirectDependencies     unused direct dependencies
   * @param unusedTransitiveDependencies unused transitive dependencies
   * @param unusedInheritedDependencies  unused inherited dependencies
   * @param dependencyClassesMap         the whole dependencies and their relates classes and used classes
   */
  public ProjectDependencyAnalysis(
      Set<DependencyCoordinate> usedDirectDependencies,
      Set<DependencyCoordinate> usedTransitiveDependencies,
      Set<DependencyCoordinate> usedInheritedDependencies,
      Set<DependencyCoordinate> unusedDirectDependencies,
      Set<DependencyCoordinate> unusedTransitiveDependencies,
      Set<DependencyCoordinate> unusedInheritedDependencies,
      Map<DependencyCoordinate, DependencyTypes> dependencyClassesMap) {
    this.usedDirectDependencies = copyOf(usedDirectDependencies);
    this.usedTransitiveDependencies = copyOf(usedTransitiveDependencies);
    this.usedInheritedDependencies = copyOf(usedInheritedDependencies);
    this.unusedDirectDependencies = copyOf(unusedDirectDependencies);
    this.unusedTransitiveDependencies = copyOf(unusedTransitiveDependencies);
    this.unusedInheritedDependencies = copyOf(unusedInheritedDependencies);
    this.dependencyClassesMap = dependencyClassesMap;
  }

  public boolean hasUsedTransitiveDependencies() {
    return !usedTransitiveDependencies.isEmpty();
  }

  public boolean hasUnusedDirectDependencies() {
    return !unusedDirectDependencies.isEmpty();
  }

  public boolean hasUnusedTransitiveDependencies() {
    return !unusedTransitiveDependencies.isEmpty();
  }

  public boolean hasUnusedInheritedDependencies() {
    return !unusedInheritedDependencies.isEmpty();
  }

  /**
   * Calculates information about the dependency once analysed.
   *
   * @param coordinate the dependency coordinate (groupId:dependencyId:version)
   * @return the information about the dependency
   */
  public DependencyAnalysisInfo getDependencyInfo(String coordinate) {
    final DependencyCoordinate dependencyCoordinate = findByCoordinates(coordinate);
    return new DependencyAnalysisInfo(
        getStatus(dependencyCoordinate),
        getType(dependencyCoordinate),
        toValue(dependencyClassesMap.get(dependencyCoordinate).getAllTypes()),
        toValue(dependencyClassesMap.get(dependencyCoordinate).getUsedTypes())
    );
  }

  private DependencyCoordinate findByCoordinates(String coordinate) {
    return dependencyClassesMap.keySet().stream()
        .filter(dc -> dc.toString().contains(coordinate))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Unable to find " + coordinate + " in dependencies"));
  }

  private TreeSet<String> toValue(Set<ClassName> types) {
    return types.stream()
        .map(ClassName::getValue)
        .collect(toCollection(TreeSet::new));
  }

  private String getStatus(DependencyCoordinate coordinates) {
    return (usedDirectDependencies.contains(coordinates) || usedInheritedDependencies
        .contains(coordinates) || usedTransitiveDependencies.contains(coordinates))
        ? "used" :
        (unusedDirectDependencies.contains(coordinates) || unusedInheritedDependencies
            .contains(coordinates) || unusedTransitiveDependencies.contains(coordinates))
            ? "bloated" : "unknown";
  }

  private String getType(DependencyCoordinate coordinates) {
    return (usedDirectDependencies.contains(coordinates) || unusedDirectDependencies
        .contains(coordinates)) ? "direct" :
        (usedInheritedDependencies.contains(coordinates) || unusedInheritedDependencies
            .contains(coordinates)) ? "inherited" :
            (usedTransitiveDependencies.contains(coordinates) || unusedTransitiveDependencies
                .contains(coordinates)) ? "transitive" : "unknown";
  }
}
