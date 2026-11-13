package se.kth.depclean.util.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.internal.DefaultDependencyNode;
import org.junit.jupiter.api.Test;
import se.kth.depclean.core.analysis.DependencyTypes;
import se.kth.depclean.core.analysis.graph.ClassUsageGraph;
import se.kth.depclean.core.analysis.graph.DependencyGraph;
import se.kth.depclean.core.analysis.model.ProjectDependencyAnalysis;
import se.kth.depclean.core.model.ClassName;
import se.kth.depclean.core.model.Dependency;

/** Unit tests for {@code depclean-results.json} and the call-graph CSV written alongside it. */
class DependencyTreeJsonWriterTest {

  private static final Dependency PROJECT =
      new Dependency("org.foo.bar", "foobar", "1.0.0-SNAPSHOT", null);
  private static final Dependency USED_LIB =
      new Dependency("org.dep", "used-lib", "1.0", "compile", null);
  private static final Dependency OTHER_LIB =
      new Dependency("org.dep", "other-lib", "2.0", "compile", null);

  private static final ClassUsageGraph CLASS_USAGE_GRAPH =
      new ClassUsageGraph(
          ImmutableSet.of("org.foo.App"),
          ImmutableMap.of(
              "org.foo.App",
              ImmutableSet.of("org.dep.used.Api", "java.lang.Object", "org.dep.other.Thing"),
              "org.dep.used.Api",
              ImmutableSet.of("org.dep.used.Impl")));

  @Test
  void csvListsEachEdgeOnceUnderTheDependencyOwningItsTarget() throws Exception {
    StringWriter csv = new StringWriter();

    String json = new DependencyTreeJsonWriter(analysis(), csv).write(tree());

    assertEquals(
        Arrays.asList(
            "org.dep.used.Api,org.dep.used.Impl,org.dep:used-lib:jar:1.0:compile",
            "org.foo.App,org.dep.used.Api,org.dep:used-lib:jar:1.0:compile",
            "org.foo.App,org.dep.other.Thing,org.dep:other-lib:jar:2.0:compile"),
        Arrays.asList(csv.toString().split("\n")));
    assertTrue(json.contains("\"coordinates\": \"org.dep:used-lib:1.0\""));
    assertTrue(json.contains("\"coordinates\": \"org.dep:other-lib:2.0\""));
  }

  @Test
  void jsonDescribesEveryNodeOfTheTreeWithItsAnalysisResult() throws Exception {
    JsonObject root =
        JsonParser.parseString(new DependencyTreeJsonWriter(analysis(), null).write(tree()))
            .getAsJsonObject();

    assertEquals("org.foo.bar:foobar:jar:1.0.0-SNAPSHOT:null", root.get("id").getAsString());
    assertEquals("unknown", root.get("parent").getAsString());
    assertTrue(!root.has("scope"));

    JsonArray children = root.getAsJsonArray("children");
    assertEquals(2, children.size());
    JsonObject usedLib = children.get(0).getAsJsonObject();
    assertEquals("org.dep:used-lib:jar:1.0:compile", usedLib.get("id").getAsString());
    assertEquals("compile", usedLib.get("scope").getAsString());
    assertEquals("jar", usedLib.get("packaging").getAsString());
    assertEquals("direct", usedLib.get("type").getAsString());
    assertEquals("used", usedLib.get("status").getAsString());
    assertEquals("org.foo.bar:foobar:jar:1.0.0-SNAPSHOT", usedLib.get("parent").getAsString());
    assertEquals(2, usedLib.getAsJsonArray("allTypes").size());
    assertEquals(1, usedLib.getAsJsonArray("usedTypes").size());
    assertEquals(0.5, usedLib.get("usageRatio").getAsDouble());

    JsonObject otherLib = children.get(1).getAsJsonObject();
    assertEquals("bloated", otherLib.get("status").getAsString());
    assertEquals(0.0, otherLib.get("usageRatio").getAsDouble());
    assertEquals(0, otherLib.getAsJsonArray("children").size());
  }

  /** The project with its two direct dependencies. */
  private static DependencyNode tree() {
    DefaultDependencyNode root =
        new DefaultDependencyNode(
            null, artifact("org.foo.bar", "foobar", "1.0.0-SNAPSHOT", null), null, null, null);
    DefaultDependencyNode usedLib =
        new DefaultDependencyNode(
            root, artifact("org.dep", "used-lib", "1.0", "compile"), null, null, null);
    usedLib.setChildren(Collections.emptyList());
    DefaultDependencyNode otherLib =
        new DefaultDependencyNode(
            root, artifact("org.dep", "other-lib", "2.0", "compile"), null, null, null);
    otherLib.setChildren(Collections.emptyList());
    root.setChildren(Arrays.asList(usedLib, otherLib));
    return root;
  }

  private static Artifact artifact(
      String groupId, String artifactId, String version, String scope) {
    return new DefaultArtifact(
        groupId, artifactId, version, scope, "jar", null, new DefaultArtifactHandler("jar"));
  }

  private static ProjectDependencyAnalysis analysis() {
    return new ProjectDependencyAnalysis(
        ImmutableSet.of(USED_LIB),
        ImmutableSet.of(),
        ImmutableSet.of(),
        ImmutableSet.of(),
        ImmutableSet.of(OTHER_LIB),
        ImmutableSet.of(),
        ImmutableSet.of(),
        ImmutableSet.of(),
        ImmutableSet.of(),
        ImmutableMap.of(
            PROJECT, types(ImmutableSet.of("org.foo.App"), ImmutableSet.of()),
            USED_LIB,
                types(
                    ImmutableSet.of("org.dep.used.Api", "org.dep.used.Impl"),
                    ImmutableSet.of("org.dep.used.Api")),
            OTHER_LIB, types(ImmutableSet.of("org.dep.other.Thing"), ImmutableSet.of())),
        new StubDependencyGraph(),
        CLASS_USAGE_GRAPH);
  }

  private static DependencyTypes types(Set<String> all, Set<String> used) {
    return new DependencyTypes(toClassNames(all), toClassNames(used));
  }

  private static Set<ClassName> toClassNames(Set<String> names) {
    ImmutableSet.Builder<ClassName> builder = ImmutableSet.builder();
    names.forEach(name -> builder.add(new ClassName(name)));
    return builder.build();
  }

  private static final class StubDependencyGraph implements DependencyGraph {
    @Override
    public Dependency projectCoordinates() {
      return PROJECT;
    }

    @Override
    public Set<Dependency> directDependencies() {
      return ImmutableSet.of(USED_LIB, OTHER_LIB);
    }

    @Override
    public Set<Dependency> transitiveDependencies() {
      return ImmutableSet.of();
    }

    @Override
    public Set<Dependency> inheritedDirectDependencies() {
      return ImmutableSet.of();
    }

    @Override
    public Set<Dependency> inheritedTransitiveDependencies() {
      return ImmutableSet.of();
    }

    @Override
    public Set<Dependency> allDependencies() {
      return directDependencies();
    }

    @Override
    public Set<Dependency> getDependenciesForParent(Dependency parent) {
      return ImmutableSet.of();
    }
  }
}
