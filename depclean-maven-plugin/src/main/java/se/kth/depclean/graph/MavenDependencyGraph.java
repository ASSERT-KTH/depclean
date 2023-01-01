package se.kth.depclean.graph;

import static com.google.common.collect.ImmutableSet.copyOf;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Sets.newHashSet;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import java.io.File;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.jetbrains.annotations.NotNull;
import se.kth.depclean.core.analysis.graph.DependencyGraph;
import se.kth.depclean.core.model.Dependency;

/**
 * A dependency graph for maven reactor.
 */
@Slf4j
public class MavenDependencyGraph implements DependencyGraph {

  private final Set<Dependency> allDependencies;
  private final MavenProject project;
  private final DependencyNode rootNode;
  private final Set<Dependency> directDependencies;
  private final Set<Dependency> transitiveDependencies;
  private final Set<Dependency> inheritedDirectDependencies;
  private final Set<Dependency> inheritedTransitiveDependencies;
  private final Multimap<Dependency, Dependency> dependenciesPerDependency = ArrayListMultimap.create();

  /**
   * Create a maven dependency graph.
   *
   * @param project  the maven project
   * @param rootNode the graph's root node
   */
  public MavenDependencyGraph(MavenProject project, Model model, DependencyNode rootNode) {
    this.project = project;
    this.rootNode = rootNode;
    buildDependencyDependencies(rootNode);
    this.allDependencies = getAllDependencies(project);
    // The model gets only the direct dependencies (not the inherited ones)
    this.directDependencies = getDirectDependencies(model);
    // The project gets all the direct dependencies (with the inherited ones)
    //noinspection deprecation
    this.inheritedDirectDependencies = inheritedDirectDependencies(project.getDependencyArtifacts());
    this.inheritedTransitiveDependencies = inheritedTransitiveDependencies(inheritedDirectDependencies, new HashSet<>());
    this.transitiveDependencies = transitiveDependencies(allDependencies);

    log.debug("Direct dependencies" + directDependencies);
    log.debug("Inherited direct dependencies" + inheritedDirectDependencies);
    log.debug("Inherited transitive dependencies" + inheritedTransitiveDependencies);
    log.debug("Transitive dependencies" + transitiveDependencies);

    // Logs
    if (log.isDebugEnabled()) {
      this.allDependencies.forEach(dep -> {
        log.debug("Found dependency {}", dep);
        if (dependenciesPerDependency.get(dep) != null) {
          dependenciesPerDependency.get(dep).forEach(transDep -> log.debug("# {}", transDep));
        }
      });
    }
  }

  @Override
  public Dependency projectCoordinates() {
    File projectJarFile;
    if (new File(project.getBuild().getDirectory() + File.separator + project.getBuild().getFinalName() + ".jar").exists()) {
      projectJarFile = new File(project.getBuild().getDirectory() + File.separator + project.getBuild().getFinalName() + ".jar");
    } else {
      projectJarFile = null;
    }
    return new Dependency(
        rootNode.getArtifact().getGroupId(),
        rootNode.getArtifact().getArtifactId(),
        rootNode.getArtifact().getVersion(),
        projectJarFile
    );
  }

  @Override
  public Set<Dependency> directDependencies() {
    return directDependencies;
  }

  @Override
  public Set<Dependency> transitiveDependencies() {
    return transitiveDependencies;
  }

  @NotNull
  private Set<Dependency> transitiveDependencies(Set<Dependency> allDependencies) {
    Set<Dependency> allTransitiveDependencies = newHashSet(allDependencies);
    allTransitiveDependencies.removeAll(this.directDependencies);
    allTransitiveDependencies.removeAll(this.inheritedDirectDependencies);
    allTransitiveDependencies.removeAll(this.inheritedTransitiveDependencies);
    return copyOf(allTransitiveDependencies);
  }

  @Override
  public Set<Dependency> inheritedDirectDependencies() {
    return inheritedDirectDependencies;
  }

  @NotNull
  private Set<Dependency> inheritedDirectDependencies(Set<Artifact> dependencyArtifacts) {
    final Set<Dependency> visibleDependencies = dependencyArtifacts.stream()
        .map(this::toDepCleanDependency)
        .collect(Collectors.toSet());
    visibleDependencies.removeAll(this.directDependencies);
    return copyOf(visibleDependencies);
  }

  @Override
  public Set<Dependency> inheritedTransitiveDependencies() {
    return inheritedTransitiveDependencies;
  }

  @NotNull
  private Set<Dependency> inheritedTransitiveDependencies(Set<Dependency> inheritedDirectDependencies, Set<Dependency> inheritedTransitiveDependencies) {
    if (!inheritedDirectDependencies.isEmpty()) {
      for (Dependency inheritedDirectDependency : inheritedDirectDependencies) {
        Set<Dependency> c = new HashSet<>(dependenciesPerDependency.get(inheritedDirectDependency));
        for (Dependency d : c) {
          project.getArtifacts().stream()
              .filter(artifact -> artifact.getGroupId().equals(d.getGroupId()) && artifact.getArtifactId().equals(d.getDependencyId()))
              .findFirst()
              .ifPresent(artifact -> {
                if (artifact.getVersion().equals(d.getVersion())) {
                  inheritedTransitiveDependencies.add(toDepCleanDependency(artifact));
                }
              });
        }
        inheritedTransitiveDependencies(c, inheritedTransitiveDependencies);
      }
    }
    return copyOf(inheritedTransitiveDependencies);
  }

  @Override
  public Set<Dependency> getDependenciesForParent(Dependency parent) {
    return copyOf(dependenciesPerDependency.get(parent));
  }

  @Override
  public Set<Dependency> allDependencies() {
    return allDependencies;
  }

  private void buildDependencyDependencies(DependencyNode parentNode) {
    parentNode.getChildren().forEach(childNode -> {
      dependenciesPerDependency.put(toDepCleanDependency(parentNode.getArtifact()), toDepCleanDependency(childNode.getArtifact()));
      buildDependencyDependencies(childNode);
    });
  }

  private Dependency toDepCleanDependency(Artifact artifact) {
    return new Dependency(
        artifact.getGroupId(),
        artifact.getArtifactId(),
        artifact.getVersion(),
        artifact.getScope(),
        artifact.getFile()
    );
  }

  private Dependency toDepCleanDependency(DependencyNode node) {
    return toDepCleanDependency(node.getArtifact());
  }

  private Dependency toDepCleanDependency(org.apache.maven.model.Dependency dependency) {
    for (Dependency artifact : allDependencies) {
      if (matches(artifact, dependency)) {
        return Optional.of(artifact).get();
      }
    }
    // This should never happen.
    return null;
  }

  private boolean matches(Dependency dependencyCoordinate, org.apache.maven.model.Dependency dependency) {
    return dependencyCoordinate.getGroupId().equalsIgnoreCase(dependency.getGroupId())
        && dependencyCoordinate.getDependencyId().equalsIgnoreCase(dependency.getArtifactId());
  }

  private ImmutableSet<Dependency> getAllDependencies(MavenProject project) {
    return project.getArtifacts().stream()
        .map(this::toDepCleanDependency)
        .collect(toImmutableSet());
  }

  private ImmutableSet<Dependency> getDirectDependencies(Model model) {
    return model.getDependencies().stream()
        .map(this::toDepCleanDependency)
        .collect(toImmutableSet());
  }
}
