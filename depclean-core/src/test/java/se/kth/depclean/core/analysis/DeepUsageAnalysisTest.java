package se.kth.depclean.core.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.kth.depclean.core.analysis.graph.ClassUsageGraph;
import se.kth.depclean.core.analysis.graph.DependencyGraph;
import se.kth.depclean.core.analysis.model.ProjectDependencyAnalysis;
import se.kth.depclean.core.model.ClassName;
import se.kth.depclean.core.model.Dependency;
import se.kth.depclean.core.model.ProjectContext;
import se.kth.depclean.core.model.Scope;
import se.kth.depclean.core.testing.Bytecode;

/** The usages that are not plain references from the bytecode of the project. */
class DeepUsageAnalysisTest {

  @TempDir Path temp;

  private Dependency api;
  private Dependency provider;
  private Dependency providerInternals;
  private Dependency unrelatedProvider;
  private Dependency assertions;
  private Dependency configured;
  private Dependency configuredInternals;
  private Path mainClasses;
  private Path testClasses;

  @BeforeEach
  void compileFixture() {
    Path apiJar =
        jar(
            "api",
            Collections.emptyList(),
            "package api; public interface Codec { String name(); }",
            "package api; public class Codecs {"
                + "  public static Iterable<Codec> all() {"
                + "    return java.util.ServiceLoader.load(Codec.class); } }",
            "package api; public interface Unreferenced { }");
    api = dependency("api", "compile", apiJar);

    Path internalsJar =
        jar("internals", Collections.emptyList(), "package internals; public class Tables { }");
    providerInternals = dependency("internals", "runtime", internalsJar);

    // registers an implementation of the service that the project reaches through api.Codecs
    Path providerClasses =
        Bytecode.compile(
            temp.resolve("provider"),
            Arrays.asList(apiJar, internalsJar),
            "package provider; public class FastCodec implements api.Codec {"
                + "  public String name() { return new internals.Tables().toString(); } }");
    Bytecode.resource(
        providerClasses,
        "META-INF/services/api.Codec",
        "# the fast one\nprovider.FastCodec # trailing comment\n\n");
    provider =
        dependency(
            "provider", "runtime", Bytecode.jar(temp.resolve("provider.jar"), providerClasses));

    // registers an implementation of a service that nothing reaches
    Path unrelatedClasses =
        Bytecode.compile(
            temp.resolve("unrelated"),
            Collections.singletonList(apiJar),
            "package unrelated; public class Impl implements api.Unreferenced { }");
    Bytecode.resource(unrelatedClasses, "META-INF/services/api.Unreferenced", "unrelated.Impl\n");
    unrelatedProvider =
        dependency(
            "unrelated", "runtime", Bytecode.jar(temp.resolve("unrelated.jar"), unrelatedClasses));

    Path assertionsJar =
        jar(
            "assertions",
            Collections.emptyList(),
            "package assertions; public class Check { public static void that(boolean b) { } }");
    assertions = dependency("assertions", "compile", assertionsJar);

    Path configuredInternalsJar =
        jar(
            "configured-internals",
            Collections.emptyList(),
            "package configuredinternals; public class Pool { }");
    configuredInternals = dependency("configured-internals", "compile", configuredInternalsJar);
    Path configuredJar =
        jar(
            "configured",
            Collections.singletonList(configuredInternalsJar),
            "package configured; public class DataSource { configuredinternals.Pool pool; }");
    configured = dependency("configured", "compile", configuredJar);

    mainClasses =
        Bytecode.compile(
            temp.resolve("main"),
            Collections.singletonList(apiJar),
            "package app; public class Main { Object codecs() { return api.Codecs.all(); } }");
    testClasses =
        Bytecode.compile(
            temp.resolve("test"),
            Arrays.asList(mainClasses, assertionsJar),
            "package app; public class MainTest {"
                + "  void test() { assertions.Check.that(new Main() != null); } }");
  }

  @Test
  void aProviderOfAReachableServiceIsUsedTogetherWithWhatItNeeds() {
    ProjectDependencyAnalysis analysis = analyze(Collections.emptySet(), Collections.emptySet());

    assertThat(analysis.getUsedDirectDependencies()).contains(api, provider);
    assertThat(analysis.getUsedTransitiveDependencies()).contains(providerInternals);
    assertThat(analysis.getClassUsageGraph().getServiceProviders())
        .containsEntry("api.Codec", ImmutableSet.of("provider.FastCodec"));
  }

  @Test
  void aProviderOfAServiceThatNothingReachesIsNotUsed() {
    ProjectDependencyAnalysis analysis = analyze(Collections.emptySet(), Collections.emptySet());

    assertThat(analysis.getUnusedDirectDependencies()).contains(unrelatedProvider);
    assertThat(analysis.getClassUsageGraph().getServiceProviders())
        .doesNotContainKey("api.Unreferenced");
  }

  @Test
  void whatAConfiguredClassNeedsIsUsedToo() {
    // e.g. a class that is only named in a Spring XML file
    ProjectDependencyAnalysis analysis =
        analyze(ImmutableSet.of(new ClassName("configured.DataSource")), Collections.emptySet());

    assertThat(analysis.getUsedDirectDependencies()).contains(configured);
    assertThat(analysis.getUsedTransitiveDependencies()).contains(configuredInternals);
  }

  @Test
  void aPackagedDependencyThatOnlyTheTestsUseCanBeDeclaredWithTheTestScope() {
    ProjectDependencyAnalysis analysis = analyze(Collections.emptySet(), Collections.emptySet());

    assertThat(analysis.getUsedDirectDependencies()).contains(assertions);
    assertThat(analysis.getDependenciesUsedOnlyByTests()).containsExactly(assertions);
  }

  @Test
  void aDependencyThatTheMainCodeIsConfiguredWithIsNotTestOnly() {
    ProjectDependencyAnalysis analysis =
        analyze(ImmutableSet.of(new ClassName("assertions.Check")), Collections.emptySet());

    assertThat(analysis.getDependenciesUsedOnlyByTests()).isEmpty();
  }

  @Test
  void nothingIsTestOnlyWhenTheTestsAreIgnored() {
    ProjectContext context =
        new ProjectContext(
            new Graph(),
            ImmutableSet.of(mainClasses),
            ImmutableSet.of(testClasses),
            ImmutableSet.of(new Scope("test")),
            Collections.emptySet(),
            Collections.emptySet(),
            Collections.emptySet());

    ProjectDependencyAnalysis analysis = new DefaultProjectDependencyAnalyzer().analyze(context);

    assertThat(analysis.getUnusedDirectDependencies()).contains(assertions);
    assertThat(analysis.getDependenciesUsedOnlyByTests()).isEmpty();
  }

  @Test
  void aTestEngineIsUsedByTheLauncherOfTheTests() {
    Path platformJar =
        jar(
            "platform",
            Collections.emptyList(),
            "package org.junit.platform.engine; public interface TestEngine { }");
    Path engineClasses =
        Bytecode.compile(
            temp.resolve("engine"),
            Collections.singletonList(platformJar),
            "package engine;"
                + " public class Engine implements org.junit.platform.engine.TestEngine { }");
    Bytecode.resource(
        engineClasses, "META-INF/services/org.junit.platform.engine.TestEngine", "engine.Engine");
    Path engineJar = Bytecode.jar(temp.resolve("engine.jar"), engineClasses);

    List<java.io.File> classpath = Arrays.asList(platformJar.toFile(), engineJar.toFile());
    ClassUsageGraph withTests =
        new ClassUsageAnalyzer()
            .analyze(
                Collections.singletonList(mainClasses),
                Collections.singletonList(testClasses),
                classpath,
                Collections.emptySet(),
                Collections.emptySet());
    ClassUsageGraph withoutTests =
        new ClassUsageAnalyzer().analyze(Collections.singletonList(mainClasses), classpath);

    assertThat(withTests.getReachableClasses()).contains("engine.Engine");
    assertThat(withTests.getClassesReachableFromMain()).doesNotContain("engine.Engine");
    assertThat(withoutTests.getReachableClasses()).doesNotContain("engine.Engine");
  }

  private ProjectDependencyAnalysis analyze(Set<ClassName> extra, Set<ClassName> extraTest) {
    return new DefaultProjectDependencyAnalyzer()
        .analyze(
            new ProjectContext(
                new Graph(),
                ImmutableSet.of(mainClasses),
                ImmutableSet.of(testClasses),
                Collections.emptySet(),
                Collections.emptySet(),
                extra,
                extraTest));
  }

  private Path jar(String name, List<Path> classpath, String... sources) {
    return Bytecode.jar(
        temp.resolve(name + ".jar"), Bytecode.compile(temp.resolve(name), classpath, sources));
  }

  private static Dependency dependency(String name, String scope, Path jar) {
    return new Dependency("org.fixture", name, "1.0", scope, jar.toFile());
  }

  private final class Graph implements DependencyGraph {

    @Override
    public Dependency projectCoordinates() {
      return new Dependency("org.fixture", "app", "1.0", null);
    }

    @Override
    public Set<Dependency> directDependencies() {
      return ImmutableSet.of(api, provider, unrelatedProvider, assertions, configured);
    }

    @Override
    public Set<Dependency> transitiveDependencies() {
      return ImmutableSet.of(providerInternals, configuredInternals);
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
      return ImmutableSet.<Dependency>builder()
          .addAll(directDependencies())
          .addAll(transitiveDependencies())
          .build();
    }

    @Override
    public Set<Dependency> getDependenciesForParent(Dependency parent) {
      return ImmutableSet.of();
    }
  }
}
