package se.kth.depclean.graph;

import static com.google.common.collect.ImmutableSet.copyOf;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Sets.newHashSet;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.io.File;
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
  private final Set<Dependency> inheritedDependencies;
  private final Set<Dependency> transitiveDependencies;
  private final Multimap<Dependency, Dependency> dependenciesPerDependency
      = ArrayListMultimap.create();

  /**
   * Create a maven dependency graph.
   *
   * @param project the maven project
   * @param rootNode the graph's root node
   */
  public MavenDependencyGraph(MavenProject project, Model model, DependencyNode rootNode) {
    this.project = project;
    this.rootNode = rootNode;

    this.allDependencies = project.getArtifacts().stream()
        .map(this::toDepCleanDependency)
        .collect(toImmutableSet());
    // The model gets only the direct dependencies (not the inherited ones)
    this.directDependencies = model.getDependencies().stream()
        .map(this::toDepCleanDependency)
        .collect(toImmutableSet());
    // The project gets all the direct dependencies (with the inherited ones)
    //noinspection deprecation
    this.inheritedDependencies = inheritedDependencies(project.getDependencyArtifacts());
    this.transitiveDependencies = transitiveDependencies(allDependencies);

    buildDependencyDependencies(rootNode);

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
    log.info("project's jar {}", project.getBuild().getDirectory() + "/" + project.getBuild().getFinalName());
    return new Dependency(
        rootNode.getArtifact().getGroupId(),
        rootNode.getArtifact().getArtifactId(),
        rootNode.getArtifact().getVersion(),
        new File(project.getBuild().getDirectory() + "/" + project.getBuild().getFinalName() + ".jar")
    );
  }

  @Override
  public Set<Dependency> allDependencies() {
    return allDependencies;
  }

  @Override
  public Set<Dependency> getDependenciesForParent(Dependency parent) {
    return copyOf(dependenciesPerDependency.get(parent));
  }

  @Override
  public Set<Dependency> directDependencies() {
    return directDependencies;
  }

  @Override
  public Set<Dependency> inheritedDependencies() {
    return inheritedDependencies;
  }

  @NotNull
  private Set<Dependency> inheritedDependencies(Set<Artifact> dependencyArtifacts) {
    final Set<Dependency> visibleDependencies = dependencyArtifacts.stream()
        .map(this::toDepCleanDependency)
        .collect(Collectors.toSet());
    visibleDependencies.removeAll(this.directDependencies);
    return copyOf(visibleDependencies);
  }

  @Override
  public Set<Dependency> transitiveDependencies() {
    return transitiveDependencies;
  }

  @NotNull
  private Set<Dependency> transitiveDependencies(Set<Dependency> allArtifactsFound) {
    final Set<Dependency> transitiveDependencies = newHashSet(allArtifactsFound);
    transitiveDependencies.removeAll(this.directDependencies);
    transitiveDependencies.removeAll(this.inheritedDependencies);
    return copyOf(transitiveDependencies);
  }

  private void buildDependencyDependencies(DependencyNode parentNode) {
    for (DependencyNode child : parentNode.getChildren()) {
      if (!child.getChildren().isEmpty()) {
        child.getChildren().forEach(c -> {
          dependenciesPerDependency.put(toDepCleanDependency(child), toDepCleanDependency(c));
          buildDependencyDependencies(c);
        });
      }
    }
  }

  private Dependency toDepCleanDependency(Artifact artifact) {
    return new Dependency(
        artifact.getGroupId(),
        artifact.getArtifactId(),
        artifact.getVersion(),
        artifact.getScope(),
        artifact.getFile());
  }

  private Dependency toDepCleanDependency(DependencyNode node) {
    return toDepCleanDependency(node.getArtifact());
  }

  private Dependency toDepCleanDependency(org.apache.maven.model.Dependency dependency) {
    //noinspection OptionalGetWithoutIsPresent
    return allDependencies.stream()
        .filter(artifact -> matches(artifact, dependency))
        .findFirst()
        .get();
  }

  private boolean matches(Dependency dependencyCoordinate, org.apache.maven.model.Dependency dependency) {
    return dependencyCoordinate.getGroupId().equalsIgnoreCase(dependency.getGroupId())
        && dependencyCoordinate.getDependencyId().equalsIgnoreCase(dependency.getArtifactId());
  }
}
