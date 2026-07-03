package se.kth.depclean.core.analysis.model;

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

import static java.util.stream.Collectors.toCollection;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import se.kth.depclean.core.analysis.DependencyTypes;
import se.kth.depclean.core.analysis.graph.DependencyGraph;
import se.kth.depclean.core.model.ClassName;
import se.kth.depclean.core.model.Dependency;

/** Project dependencies analysis result. */
@Getter
@EqualsAndHashCode
@Slf4j
public class ProjectDependencyAnalysis {

  private static final String SEPARATOR = "-------------------------------------------------------";
  private final Set<Dependency> usedDirectDependencies;
  private final Set<Dependency> usedTransitiveDependencies;
  private final Set<Dependency> usedInheritedDirectDependencies;
  private final Set<Dependency> usedInheritedTransitiveDependencies;
  private final Set<Dependency> unusedDirectDependencies;
  private final Set<Dependency> unusedTransitiveDependencies;
  private final Set<Dependency> unusedInheritedDirectDependencies;
  private final Set<Dependency> unusedInheritedTransitiveDependencies;
  private final Set<Dependency> ignoredDependencies;
  private final Map<Dependency, DependencyTypes> dependencyClassesMap;
  private final DependencyGraph dependencyGraph;

  private ProjectDependencyAnalysis(Builder builder) {
    this.usedDirectDependencies = ImmutableSet.copyOf(builder.usedDirectDependencies);
    this.usedTransitiveDependencies = ImmutableSet.copyOf(builder.usedTransitiveDependencies);
    this.usedInheritedDirectDependencies =
        ImmutableSet.copyOf(builder.usedInheritedDirectDependencies);
    this.usedInheritedTransitiveDependencies =
        ImmutableSet.copyOf(builder.usedInheritedTransitiveDependencies);
    this.unusedDirectDependencies = ImmutableSet.copyOf(builder.unusedDirectDependencies);
    this.unusedTransitiveDependencies = ImmutableSet.copyOf(builder.unusedTransitiveDependencies);
    this.unusedInheritedDirectDependencies =
        ImmutableSet.copyOf(builder.unusedInheritedDirectDependencies);
    this.unusedInheritedTransitiveDependencies =
        ImmutableSet.copyOf(builder.unusedInheritedTransitiveDependencies);
    this.ignoredDependencies = ImmutableSet.copyOf(builder.ignoredDependencies);
    this.dependencyClassesMap = ImmutableMap.copyOf(builder.dependencyClassesMap);
    this.dependencyGraph = Objects.requireNonNull(builder.dependencyGraph, "dependencyGraph");
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for project dependency analysis results. */
  public static class Builder {
    private Set<Dependency> usedDirectDependencies = ImmutableSet.of();
    private Set<Dependency> usedTransitiveDependencies = ImmutableSet.of();
    private Set<Dependency> usedInheritedDirectDependencies = ImmutableSet.of();
    private Set<Dependency> usedInheritedTransitiveDependencies = ImmutableSet.of();
    private Set<Dependency> unusedDirectDependencies = ImmutableSet.of();
    private Set<Dependency> unusedTransitiveDependencies = ImmutableSet.of();
    private Set<Dependency> unusedInheritedDirectDependencies = ImmutableSet.of();
    private Set<Dependency> unusedInheritedTransitiveDependencies = ImmutableSet.of();
    private Set<Dependency> ignoredDependencies = ImmutableSet.of();
    private Map<Dependency, DependencyTypes> dependencyClassesMap = ImmutableMap.of();
    private DependencyGraph dependencyGraph;

    public Builder usedDirectDependencies(Set<Dependency> usedDirectDependencies) {
      this.usedDirectDependencies = usedDirectDependencies;
      return this;
    }

    public Builder usedTransitiveDependencies(Set<Dependency> usedTransitiveDependencies) {
      this.usedTransitiveDependencies = usedTransitiveDependencies;
      return this;
    }

    public Builder usedInheritedDirectDependencies(
        Set<Dependency> usedInheritedDirectDependencies) {
      this.usedInheritedDirectDependencies = usedInheritedDirectDependencies;
      return this;
    }

    public Builder usedInheritedTransitiveDependencies(
        Set<Dependency> usedInheritedTransitiveDependencies) {
      this.usedInheritedTransitiveDependencies = usedInheritedTransitiveDependencies;
      return this;
    }

    public Builder unusedDirectDependencies(Set<Dependency> unusedDirectDependencies) {
      this.unusedDirectDependencies = unusedDirectDependencies;
      return this;
    }

    public Builder unusedTransitiveDependencies(Set<Dependency> unusedTransitiveDependencies) {
      this.unusedTransitiveDependencies = unusedTransitiveDependencies;
      return this;
    }

    public Builder unusedInheritedDirectDependencies(
        Set<Dependency> unusedInheritedDirectDependencies) {
      this.unusedInheritedDirectDependencies = unusedInheritedDirectDependencies;
      return this;
    }

    public Builder unusedInheritedTransitiveDependencies(
        Set<Dependency> unusedInheritedTransitiveDependencies) {
      this.unusedInheritedTransitiveDependencies = unusedInheritedTransitiveDependencies;
      return this;
    }

    public Builder ignoredDependencies(Set<Dependency> ignoredDependencies) {
      this.ignoredDependencies = ignoredDependencies;
      return this;
    }

    public Builder dependencyClassesMap(
        Map<Dependency, DependencyTypes> dependencyClassesMap) {
      this.dependencyClassesMap = dependencyClassesMap;
      return this;
    }

    public Builder dependencyGraph(DependencyGraph dependencyGraph) {
      this.dependencyGraph = dependencyGraph;
      return this;
    }

    public ProjectDependencyAnalysis build() {
      return new ProjectDependencyAnalysis(this);
    }
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

  public boolean hasUnusedInheritedDirectDependencies() {
    return !unusedInheritedDirectDependencies.isEmpty();
  }

  public boolean hasUnusedInheritedTransitiveDependencies() {
    return !unusedInheritedTransitiveDependencies.isEmpty();
  }

  /** Displays the analysis result. */
  public void print() {
    printString(SEPARATOR);
    printString(" D E P C L E A N   A N A L Y S I S   R E S U L T S");
    printString(SEPARATOR);
    printInfoOfDependencies("Used direct dependencies", getUsedDirectDependencies());
    printInfoOfDependencies("Used transitive dependencies", getUsedTransitiveDependencies());
    printInfoOfDependencies(
        "Used inherited direct dependencies", getUsedInheritedDirectDependencies());
    printInfoOfDependencies(
        "Used inherited transitive dependencies", getUsedInheritedTransitiveDependencies());
    printInfoOfDependencies(
        "Potentially unused direct dependencies", getUnusedDirectDependencies());
    printInfoOfDependencies(
        "Potentially unused transitive dependencies", getUnusedTransitiveDependencies());
    printInfoOfDependencies(
        "Potentially unused inherited direct dependencies", getUnusedInheritedDirectDependencies());
    printInfoOfDependencies(
        "Potentially unused inherited transitive dependencies",
        getUnusedInheritedTransitiveDependencies());
    if (!ignoredDependencies.isEmpty()) {
      printString(SEPARATOR);
      printString(
          "Dependencies ignored in the analysis by the user"
              + " ["
              + ignoredDependencies.size()
              + "]"
              + ":"
              + " ");
      ignoredDependencies.forEach(s -> printString("\t" + s));
    }
  }

  /**
   * Calculates information about the dependency once analysed.
   *
   * @param coordinate the dependency coordinate (groupId:dependencyId:version)
   * @return the information about the dependency
   */
  @Nullable
  public DependencyAnalysisInfo getDependencyInfo(String coordinate) {
    Dependency dependency;
    try {
      dependency = findByCoordinates(coordinate);
    } catch (RuntimeException e) {
      return null;
    }
    DependencyTypes dependencyTypes = dependencyClassesMap.get(dependency);
    if (dependencyTypes == null) {
      return null;
    }
    return new DependencyAnalysisInfo(
        getStatus(dependency),
        getType(dependency),
        dependency.getSize(),
        toValue(dependencyTypes.getAllTypes()),
        toValue(dependencyTypes.getUsedTypes()));
  }

  /**
   * Get all the used dependencies (direct, inherited and transitive).
   *
   * @return the used dependencies
   */
  public Set<DebloatedDependency> getUsedDependencies() {
    final Set<Dependency> dependencies = new HashSet<>(getUsedDirectDependencies());
    dependencies.addAll(getUsedInheritedDirectDependencies());
    dependencies.addAll(getUsedInheritedTransitiveDependencies());
    dependencies.addAll(getUsedTransitiveDependencies());
    return dependencies.stream()
        .map(this::toDebloatedDependency)
        .collect(ImmutableSet.toImmutableSet());
  }

  /**
   * Get all the potentially unused dependencies (direct, inherited and transitive).
   *
   * @return the unused dependencies
   */
  public Set<DebloatedDependency> getUnusedDependencies() {
    final Set<Dependency> dependencies = new HashSet<>(getUnusedDirectDependencies());
    dependencies.addAll(getUnusedInheritedDirectDependencies());
    dependencies.addAll(getUnusedInheritedTransitiveDependencies());
    dependencies.addAll(getUnusedTransitiveDependencies());
    return dependencies.stream()
        .map(this::toDebloatedDependency)
        .collect(ImmutableSet.toImmutableSet());
  }

  private Dependency findByCoordinates(String coordinate) {
    return dependencyClassesMap.keySet().stream()
        .filter(dc -> dc.toString().contains(coordinate))
        .findFirst()
        .orElseThrow(
            () -> new RuntimeException("Unable to find " + coordinate + " in dependencies"));
  }

  @SuppressWarnings("NonApiType") // TreeSet required by DependencyAnalysisInfo constructor
  private TreeSet<String> toValue(Set<ClassName> types) {
    return types.stream().map(ClassName::getValue).collect(toCollection(TreeSet::new));
  }

  private String getStatus(Dependency coordinates) {
    if (usedDirectDependencies.contains(coordinates)
        || usedInheritedDirectDependencies.contains(coordinates)
        || usedInheritedTransitiveDependencies.contains(coordinates)
        || usedTransitiveDependencies.contains(coordinates)) {
      return "used";
    } else {
      return (unusedDirectDependencies.contains(coordinates)
              || unusedInheritedDirectDependencies.contains(coordinates)
              || unusedInheritedTransitiveDependencies.contains(coordinates)
              || unusedTransitiveDependencies.contains(coordinates))
          ? "bloated"
          : "unknown";
    }
  }

  private String getType(Dependency coordinates) {
    if (usedDirectDependencies.contains(coordinates)
        || unusedDirectDependencies.contains(coordinates)) {
      return "direct";
    } else if ((usedInheritedDirectDependencies.contains(coordinates)
        || usedInheritedTransitiveDependencies.contains(coordinates)
        || unusedInheritedDirectDependencies.contains(coordinates)
        || unusedInheritedTransitiveDependencies.contains(coordinates))) {
      return "inherited";
    } else {
      return (usedTransitiveDependencies.contains(coordinates)
              || unusedTransitiveDependencies.contains(coordinates))
          ? "transitive"
          : "unknown";
    }
  }

  private void printString(final String string) {
    System.out.println(string); // NOSONAR avoid a warning of non-used logger
  }

  /**
   * Util function to print the information of the analyzed artifacts.
   *
   * @param info The usage status (used or unused) and type (direct, transitive, inherited) of
   *     artifacts.
   * @param dependencies The GAV of the artifact.
   */
  private void printInfoOfDependencies(final String info, final Set<Dependency> dependencies) {
    printString(info.toUpperCase(Locale.ROOT) + " [" + dependencies.size() + "]" + ": ");
    printDependencies(dependencies);
  }

  /**
   * Print the status of the dependencies to the standard output. The format is:
   * "[coordinates][scope] [(size)]"
   *
   * @param dependencies The set dependencies to print.
   */
  private void printDependencies(final Set<Dependency> dependencies) {
    dependencies.stream()
        .sorted(Comparator.comparing(Dependency::getSize).reversed())
        .forEach(s -> printString("\t" + s.printWithSize()));
  }

  private DebloatedDependency toDebloatedDependency(Dependency dependency) {
    final Set<Dependency> dependenciesForParent =
        dependencyGraph.getDependenciesForParent(dependency);
    final Set<Dependency> dependenciesToExclude =
        dependenciesForParent.stream()
            .filter(dep -> getUnusedTransitiveDependencies().contains(dep))
            .collect(Collectors.toSet());
    return new DebloatedDependency(dependency, ImmutableSet.copyOf(dependenciesToExclude));
  }
}
