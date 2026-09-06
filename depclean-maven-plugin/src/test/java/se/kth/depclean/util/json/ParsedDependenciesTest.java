package se.kth.depclean.util.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.kth.depclean.core.analysis.DependencyTypes;
import se.kth.depclean.core.analysis.graph.DefaultCallGraph;
import se.kth.depclean.core.analysis.graph.DependencyGraph;
import se.kth.depclean.core.analysis.model.ProjectDependencyAnalysis;
import se.kth.depclean.core.model.ClassName;
import se.kth.depclean.core.model.Dependency;

/** Unit tests for the call-graph CSV written alongside {@code depclean-results.json}. */
class ParsedDependenciesTest {

  private static final Dependency PROJECT =
      new Dependency("org.foo.bar", "foobar", "1.0.0-SNAPSHOT", null);
  private static final Dependency USED_LIB =
      new Dependency("org.dep", "used-lib", "1.0", "compile", null);
  private static final Dependency OTHER_LIB =
      new Dependency("org.dep", "other-lib", "2.0", "compile", null);

  @TempDir Path tempDir;

  @BeforeEach
  void populateCallGraph() {
    DefaultCallGraph.clear();
    DefaultCallGraph.addEdge(
        "org.foo.App",
        ImmutableSet.of("org.dep.used.Api", "java.lang.Object", "org.dep.other.Thing"));
    DefaultCallGraph.addEdge("org.dep.used.Api", ImmutableSet.of("org.dep.used.Impl"));
  }

  @AfterEach
  void clearCallGraph() {
    DefaultCallGraph.clear();
  }

  @Test
  void csvListsEachEdgeOnceUnderTheDependencyOwningItsTarget() throws Exception {
    File treeFile = writeTree();
    File csvFile = tempDir.resolve("depclean-callgraph.csv").toFile();
    // DepCleanManager (re)creates the file with the header before the tree is written.
    Files.write(
        csvFile.toPath(),
        "OriginClass,TargetClass,TargetDependency\n".getBytes(StandardCharsets.UTF_8));

    String json = new ParsedDependencies(treeFile, analysis(), csvFile, true).parseTreeToJson();

    List<String> expected =
        Arrays.asList(
            "OriginClass,TargetClass,TargetDependency",
            "org.dep.used.Api,org.dep.used.Impl,org.dep:used-lib:jar:1.0:compile",
            "org.foo.App,org.dep.used.Api,org.dep:used-lib:jar:1.0:compile",
            "org.foo.App,org.dep.other.Thing,org.dep:other-lib:jar:2.0:compile");
    assertEquals(expected, Files.readAllLines(csvFile.toPath(), StandardCharsets.UTF_8));
    assertTrue(json.contains("\"coordinates\": \"org.dep:used-lib:1.0\""));
    assertTrue(json.contains("\"coordinates\": \"org.dep:other-lib:2.0\""));
  }

  @Test
  void csvIsNotWrittenWhenDisabled() throws Exception {
    File treeFile = writeTree();
    File csvFile = tempDir.resolve("depclean-callgraph.csv").toFile();

    new ParsedDependencies(treeFile, analysis(), csvFile, false).parseTreeToJson();

    assertTrue(!csvFile.exists());
  }

  /** A verbose tree where other-lib also appears as an omitted duplicate under used-lib. */
  private File writeTree() throws IOException {
    Path treeFile = tempDir.resolve("tree.txt");
    Files.write(
        treeFile,
        Arrays.asList(
            "org.foo.bar:foobar:jar:1.0.0-SNAPSHOT",
            "+- org.dep:used-lib:jar:1.0:compile",
            "|  \\- (org.dep:other-lib:jar:2.0:compile - omitted for duplicate)",
            "\\- org.dep:other-lib:jar:2.0:compile"),
        StandardCharsets.UTF_8);
    return treeFile.toFile();
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
        new StubDependencyGraph());
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
