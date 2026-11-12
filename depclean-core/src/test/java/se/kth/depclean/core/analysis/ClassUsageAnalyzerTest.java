package se.kth.depclean.core.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.kth.depclean.core.analysis.graph.ClassUsageGraph;
import se.kth.depclean.core.testing.Bytecode;

class ClassUsageAnalyzerTest {

  @TempDir Path temp;

  private Path libraryJar;
  private Path transitiveJar;
  private Path projectClasses;

  @BeforeEach
  void compileFixture() {
    // transitive.jar  <-  library.jar  <-  project
    Path transitiveClasses =
        Bytecode.compile(
            temp.resolve("transitive"),
            Collections.emptyList(),
            "package t; public class Engine { public void start() { } }",
            "package t; public class Spare { }");
    transitiveJar = Bytecode.jar(temp.resolve("transitive.jar"), transitiveClasses);

    Path libraryClasses =
        Bytecode.compile(
            temp.resolve("library"),
            Collections.singletonList(transitiveJar),
            "package l; public class Car { public void drive() { new t.Engine().start(); } }",
            "package l; public class Unused { t.Spare spare; }");
    libraryJar = Bytecode.jar(temp.resolve("library.jar"), libraryClasses);

    projectClasses =
        Bytecode.compile(
            temp.resolve("project"),
            Collections.singletonList(libraryJar),
            "package app; public class Main { void run() { new l.Car().drive(); helper(); }"
                + " void helper() { new Helper(); } }",
            "package app; public class Helper { }");
  }

  private ClassUsageGraph analyze() {
    return new ClassUsageAnalyzer()
        .analyze(
            Collections.singletonList(projectClasses),
            Arrays.asList(libraryJar.toFile(), transitiveJar.toFile()));
  }

  @Test
  void namesProjectClassesByTheirFullyQualifiedName() {
    assertThat(analyze().getProjectClasses()).containsExactlyInAnyOrder("app.Main", "app.Helper");
  }

  @Test
  void followsReferencesThroughTheClassesOfTheDependencies() {
    ClassUsageGraph graph = analyze();

    assertThat(graph.getReachableClasses())
        .contains("app.Main", "app.Helper", "l.Car", "t.Engine", "java.lang.Object");
    assertThat(graph.containsEdge("app.Main", "l.Car")).isTrue();
    assertThat(graph.containsEdge("l.Car", "t.Engine")).isTrue();
    assertThat(graph.containsEdge("app.Main", "t.Engine")).isFalse();
  }

  @Test
  void neverOpensClassesTheProjectCannotReach() {
    ClassUsageGraph graph = analyze();

    assertThat(graph.getReachableClasses()).doesNotContain("l.Unused", "t.Spare");
    assertThat(graph.getUsagesPerClass()).doesNotContainKeys("l.Unused", "t.Spare");
  }

  @Test
  void followsReferencesFromExtraRoots() {
    ClassUsageGraph graph =
        new ClassUsageAnalyzer()
            .analyze(
                Collections.singletonList(projectClasses),
                Collections.emptyList(),
                Arrays.asList(libraryJar.toFile(), transitiveJar.toFile()),
                Collections.singleton("l.Unused"),
                Collections.emptySet());

    assertThat(graph.getReachableClasses()).contains("l.Unused", "t.Spare");
  }

  @Test
  void readsDependenciesThatAreDirectoriesOfClasses() {
    ClassUsageGraph graph =
        new ClassUsageAnalyzer()
            .analyze(
                Collections.singletonList(projectClasses),
                Arrays.asList(temp.resolve("library").toFile(), transitiveJar.toFile()));

    assertThat(graph.getReachableClasses()).contains("l.Car", "t.Engine");
  }

  @Test
  void toleratesMissingDirectoriesBrokenJarsAndCorruptClassFiles() throws Exception {
    File brokenJar = temp.resolve("broken.jar").toFile();
    Files.write(brokenJar.toPath(), new byte[] {'n', 'o', 't', ' ', 'a', ' ', 'z', 'i', 'p'});
    Files.write(projectClasses.resolve("app/Corrupt.class"), new byte[] {0, 1, 2});

    ClassUsageGraph graph =
        new ClassUsageAnalyzer()
            .analyze(
                Arrays.asList(projectClasses, temp.resolve("does-not-exist")),
                Arrays.asList(brokenJar, libraryJar.toFile(), new File("missing.jar")));

    assertThat(graph.getProjectClasses()).containsExactlyInAnyOrder("app.Main", "app.Helper");
    assertThat(graph.getReachableClasses()).contains("l.Car");
  }

  @Test
  void leavesNothingBehindOnDisk() throws Exception {
    analyze();

    try (java.util.stream.Stream<Path> files = Files.list(temp)) {
      assertThat(files.map(path -> path.getFileName().toString()))
          .containsExactlyInAnyOrder(
              "transitive", "transitive.jar", "library", "library.jar", "project");
    }
  }
}
