package se.kth.depclean.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;
import org.junit.jupiter.api.Test;
import se.kth.depclean.core.model.Dependency;

/**
 * Tests that declared dependencies whose coordinates use property placeholders (e.g. {@code
 * <groupId>${slf4j.groupId}</groupId>}) are matched against resolved artifacts instead of crashing
 * the analysis. See https://github.com/ASSERT-KTH/depclean/issues/399.
 */
class MavenDependencyGraphTest {

  private static final String PROJECT_GROUP_ID = "org.foo.bar";

  @Test
  void resolvesPropertyPlaceholdersInDependencyCoordinates() {
    MavenProject project = createProject();
    project.getProperties().setProperty("io.groupId", "commons-io");
    project.getProperties().setProperty("io.artifactId", "commons-io");

    Model rawModel = new Model();
    rawModel.addDependency(createModelDependency("${io.groupId}", "${io.artifactId}", "2.11.0"));

    MavenDependencyGraph graph = new MavenDependencyGraph(project, rawModel, rootNode(project));

    assertThat(graph.directDependencies())
        .extracting(Dependency::getGroupId, Dependency::getDependencyId)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("commons-io", "commons-io"));
  }

  @Test
  void resolvesProjectBuiltInPlaceholders() {
    MavenProject project = createProject();

    Model rawModel = new Model();
    rawModel.addDependency(createModelDependency("${project.groupId}", "sibling", "1.0.0"));

    MavenDependencyGraph graph = new MavenDependencyGraph(project, rawModel, rootNode(project));

    assertThat(graph.directDependencies())
        .extracting(Dependency::getGroupId, Dependency::getDependencyId)
        .containsExactly(org.assertj.core.groups.Tuple.tuple(PROJECT_GROUP_ID, "sibling"));
  }

  @Test
  void matchesLiteralCoordinates() {
    MavenProject project = createProject();

    Model rawModel = new Model();
    rawModel.addDependency(createModelDependency("commons-io", "commons-io", "2.11.0"));

    MavenDependencyGraph graph = new MavenDependencyGraph(project, rawModel, rootNode(project));

    assertThat(graph.directDependencies())
        .extracting(Dependency::getGroupId, Dependency::getDependencyId)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("commons-io", "commons-io"));
  }

  @Test
  void skipsDependencyThatCannotBeMatched() {
    MavenProject project = createProject();

    Model rawModel = new Model();
    rawModel.addDependency(createModelDependency("commons-io", "commons-io", "2.11.0"));
    rawModel.addDependency(createModelDependency("${undefined.groupId}", "unknown", "1.0.0"));

    assertThatCode(() -> new MavenDependencyGraph(project, rawModel, rootNode(project)))
        .doesNotThrowAnyException();

    MavenDependencyGraph graph = new MavenDependencyGraph(project, rawModel, rootNode(project));
    assertThat(graph.directDependencies())
        .extracting(Dependency::getGroupId, Dependency::getDependencyId)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("commons-io", "commons-io"));
  }

  private MavenProject createProject() {
    MavenProject project = new MavenProject();
    project.setGroupId(PROJECT_GROUP_ID);
    project.setArtifactId("foobar");
    project.setVersion("1.0.0-SNAPSHOT");
    Set<Artifact> resolvedArtifacts =
        Set.of(
            createArtifact("commons-io", "commons-io", "2.11.0"),
            createArtifact(PROJECT_GROUP_ID, "sibling", "1.0.0"));
    project.setArtifacts(resolvedArtifacts);
    project.setDependencyArtifacts(resolvedArtifacts);
    return project;
  }

  private Artifact createArtifact(String groupId, String artifactId, String version) {
    return new DefaultArtifact(
        groupId, artifactId, version, "compile", "jar", "", new DefaultArtifactHandler("jar"));
  }

  private org.apache.maven.model.Dependency createModelDependency(
      String groupId, String artifactId, String version) {
    org.apache.maven.model.Dependency dependency = new org.apache.maven.model.Dependency();
    dependency.setGroupId(groupId);
    dependency.setArtifactId(artifactId);
    dependency.setVersion(version);
    return dependency;
  }

  private DependencyNode rootNode(MavenProject project) {
    Artifact rootArtifact =
        createArtifact(project.getGroupId(), project.getArtifactId(), project.getVersion());
    return new DependencyNode() {
      @Override
      public Artifact getArtifact() {
        return rootArtifact;
      }

      @Override
      public List<DependencyNode> getChildren() {
        return List.of();
      }

      @Override
      public boolean accept(DependencyNodeVisitor visitor) {
        return false;
      }

      @Override
      @SuppressWarnings("NullAway") // root node has no parent; not used by the code under test
      public DependencyNode getParent() {
        return null;
      }

      @Override
      public String getPremanagedVersion() {
        return "";
      }

      @Override
      public String getPremanagedScope() {
        return "";
      }

      @Override
      public String getVersionConstraint() {
        return "";
      }

      @Override
      public String toNodeString() {
        return "";
      }

      @Override
      public Boolean getOptional() {
        return Boolean.FALSE;
      }

      @Override
      public List<Exclusion> getExclusions() {
        return List.of();
      }
    };
  }
}
