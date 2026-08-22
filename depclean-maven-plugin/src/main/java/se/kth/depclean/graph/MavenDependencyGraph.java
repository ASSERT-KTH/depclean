package se.kth.depclean.graph;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Sets.newHashSet;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import java.io.File;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.depclean.core.analysis.graph.DependencyGraph;
import se.kth.depclean.core.model.Dependency;

/** A dependency graph for maven reactor. */
public class MavenDependencyGraph implements DependencyGraph {

  private static final Logger log = LoggerFactory.getLogger(MavenDependencyGraph.class);

  private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

  private final Set<Dependency> allDependencies;
  private final MavenProject project;
  private final DependencyNode rootNode;
  private final Set<Dependency> directDependencies;
  private final Set<Dependency> transitiveDependencies;
  private final Set<Dependency> inheritedDirectDependencies;
  private final Set<Dependency> inheritedTransitiveDependencies;
  private final Multimap<Dependency, Dependency> dependenciesPerDependency =
      ArrayListMultimap.create();

  /**
   * Create a maven dependency graph.
   *
   * @param project the maven project
   * @param rootNode the graph's root node
   */
  public MavenDependencyGraph(
      @NonNull MavenProject project, @NonNull Model model, @NonNull DependencyNode rootNode) {
    this.project = project;
    this.rootNode = rootNode;
    buildDependencyDependencies(rootNode);
    this.allDependencies = getAllDependencies(project);
    // The model gets only the direct dependencies (not the inherited ones)
    this.directDependencies = getDirectDependencies(model);
    // The project gets all the direct dependencies (with the inherited ones)
    // noinspection deprecation
    this.inheritedDirectDependencies =
        inheritedDirectDependencies(project.getDependencyArtifacts());
    this.inheritedTransitiveDependencies =
        inheritedTransitiveDependencies(inheritedDirectDependencies, new HashSet<>());
    this.transitiveDependencies = transitiveDependencies(allDependencies);

    log.debug("Direct dependencies" + directDependencies);
    log.debug("Inherited direct dependencies" + inheritedDirectDependencies);
    log.debug("Inherited transitive dependencies" + inheritedTransitiveDependencies);
    log.debug("Transitive dependencies" + transitiveDependencies);

    // Logs
    if (log.isDebugEnabled()) {
      this.allDependencies.forEach(
          dep -> {
            log.debug("Found dependency {}", dep);
            if (dependenciesPerDependency.containsKey(dep)) {
              dependenciesPerDependency.get(dep).forEach(transDep -> log.debug("# {}", transDep));
            }
          });
    }
  }

  @NonNull
  @Override
  public Dependency projectCoordinates() {
    File projectJarFile;
    if (new File(
            project.getBuild().getDirectory()
                + File.separator
                + project.getBuild().getFinalName()
                + ".jar")
        .exists()) {
      projectJarFile =
          new File(
              project.getBuild().getDirectory()
                  + File.separator
                  + project.getBuild().getFinalName()
                  + ".jar");
    } else {
      projectJarFile = null;
    }
    return new Dependency(
        rootNode.getArtifact().getGroupId(),
        rootNode.getArtifact().getArtifactId(),
        rootNode.getArtifact().getVersion(),
        projectJarFile);
  }

  @Override
  public Set<Dependency> directDependencies() {
    return directDependencies;
  }

  @Override
  public Set<Dependency> transitiveDependencies() {
    return transitiveDependencies;
  }

  @NonNull
  private Set<Dependency> transitiveDependencies(Set<Dependency> allDependencies) {
    Set<Dependency> allTransitiveDependencies = newHashSet(allDependencies);
    allTransitiveDependencies.removeAll(this.directDependencies);
    allTransitiveDependencies.removeAll(this.inheritedDirectDependencies);
    allTransitiveDependencies.removeAll(this.inheritedTransitiveDependencies);
    return ImmutableSet.copyOf(allTransitiveDependencies);
  }

  @Override
  public Set<Dependency> inheritedDirectDependencies() {
    return inheritedDirectDependencies;
  }

  @NonNull
  private Set<Dependency> inheritedDirectDependencies(Set<Artifact> dependencyArtifacts) {
    final Set<Dependency> visibleDependencies =
        dependencyArtifacts.stream().map(this::toDepCleanDependency).collect(Collectors.toSet());
    visibleDependencies.removeAll(this.directDependencies);
    return ImmutableSet.copyOf(visibleDependencies);
  }

  @Override
  public Set<Dependency> inheritedTransitiveDependencies() {
    return inheritedTransitiveDependencies;
  }

  @NonNull
  private Set<Dependency> inheritedTransitiveDependencies(
      Set<Dependency> inheritedDirectDependencies,
      Set<Dependency> inheritedTransitiveDependencies) {
    if (!inheritedDirectDependencies.isEmpty()) {
      for (Dependency inheritedDirectDependency : inheritedDirectDependencies) {
        Set<Dependency> c = new HashSet<>(dependenciesPerDependency.get(inheritedDirectDependency));
        for (Dependency d : c) {
          project.getArtifacts().stream()
              .filter(
                  artifact ->
                      artifact.getGroupId().equals(d.getGroupId())
                          && artifact.getArtifactId().equals(d.getDependencyId()))
              .findFirst()
              .ifPresent(
                  artifact -> {
                    if (artifact.getVersion().equals(d.getVersion())) {
                      inheritedTransitiveDependencies.add(toDepCleanDependency(artifact));
                    }
                  });
        }
        inheritedTransitiveDependencies(c, inheritedTransitiveDependencies);
      }
    }
    return ImmutableSet.copyOf(inheritedTransitiveDependencies);
  }

  @Override
  public Set<Dependency> getDependenciesForParent(Dependency parent) {
    return ImmutableSet.copyOf(dependenciesPerDependency.get(parent));
  }

  @Override
  public Set<Dependency> allDependencies() {
    return allDependencies;
  }

  private void buildDependencyDependencies(DependencyNode parentNode) {
    parentNode
        .getChildren()
        .forEach(
            childNode -> {
              dependenciesPerDependency.put(
                  toDepCleanDependency(parentNode.getArtifact()),
                  toDepCleanDependency(childNode.getArtifact()));
              buildDependencyDependencies(childNode);
            });
  }

  private Dependency toDepCleanDependency(Artifact artifact) {
    return new Dependency(
        artifact.getGroupId(),
        artifact.getArtifactId(),
        artifact.getVersion(),
        artifact.getScope(),
        artifact.getFile());
  }

  private Optional<Dependency> findDepCleanDependency(
      org.apache.maven.model.Dependency dependency) {
    for (Dependency artifact : allDependencies) {
      if (matches(artifact, dependency)) {
        return Optional.of(artifact);
      }
    }
    log.warn(
        "DepClean could not match the declared dependency {}:{} with any resolved artifact; "
            + "it will be excluded from the direct dependencies analysis.",
        dependency.getGroupId(),
        dependency.getArtifactId());
    return Optional.empty();
  }

  private boolean matches(
      Dependency dependencyCoordinate, org.apache.maven.model.Dependency dependency) {
    return dependencyCoordinate
            .getGroupId()
            .toLowerCase(Locale.ROOT)
            .equals(interpolate(dependency.getGroupId()).toLowerCase(Locale.ROOT))
        && dependencyCoordinate
            .getDependencyId()
            .toLowerCase(Locale.ROOT)
            .equals(interpolate(dependency.getArtifactId()).toLowerCase(Locale.ROOT));
  }

  /**
   * Resolves {@code ${...}} placeholders in raw pom coordinates (e.g. {@code
   * <groupId>${slf4j.groupId}</groupId>}) since the model is read from the pom file without
   * interpolation. See https://github.com/ASSERT-KTH/depclean/issues/399.
   */
  private String interpolate(String value) {
    if (value == null || !value.contains("${")) {
      return value == null ? "" : value;
    }
    Matcher matcher = PROPERTY_PATTERN.matcher(value);
    // StringBuffer overload: Matcher.appendReplacement(StringBuilder, ...) is Java 9+
    StringBuffer result = new StringBuffer();
    while (matcher.find()) {
      String resolved = resolveProperty(matcher.group(1));
      matcher.appendReplacement(
          result, Matcher.quoteReplacement(resolved == null ? matcher.group(0) : resolved));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  private String resolveProperty(String key) {
    switch (key) {
      case "project.groupId":
      case "pom.groupId":
        return project.getGroupId();
      case "project.artifactId":
      case "pom.artifactId":
        return project.getArtifactId();
      case "project.version":
      case "pom.version":
        return project.getVersion();
      default:
        return project.getProperties().getProperty(key);
    }
  }

  private ImmutableSet<Dependency> getAllDependencies(MavenProject project) {
    return project.getArtifacts().stream()
        .map(this::toDepCleanDependency)
        .collect(toImmutableSet());
  }

  private ImmutableSet<Dependency> getDirectDependencies(Model model) {
    return model.getDependencies().stream()
        .map(this::findDepCleanDependency)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(toImmutableSet());
  }
}
