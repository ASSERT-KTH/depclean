package se.kth.depclean.core.analysis.model;

import static org.assertj.core.api.Assertions.assertThat;
import static se.kth.depclean.core.analysis.ProjectContextCreator.createDependency;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import se.kth.depclean.core.analysis.ProjectContextCreator;
import se.kth.depclean.core.analysis.graph.DependencyGraph;
import se.kth.depclean.core.model.Dependency;

class ProjectDependencyAnalysisTest implements ProjectContextCreator {

  /** No jar, hence no classes: an aggregator as soon as it brings other dependencies. */
  private static final Dependency STARTER = createDependency("starter");

  private static final Dependency NESTED_STARTER = createDependency("nested-starter");

  @Test
  void shouldBuildResultingDependencyGraph() {
    final ProjectDependencyAnalysis analysis =
        new ProjectDependencyAnalysis(
            ImmutableSet.of(COMMONS_IO_DEPENDENCY),
            ImmutableSet.of(JUNIT_DEPENDENCY),
            ImmutableSet.of(),
            ImmutableSet.of(COMMONS_LANG_DEPENDENCY),
            ImmutableSet.of(COMMONS_MATH_DEPENDENCY),
            ImmutableSet.of(COMMONS_IO_DEPENDENCY),
            ImmutableSet.of(COMMONS_LOGGING_DEPENDENCY),
            ImmutableSet.of(),
            ImmutableSet.of(),
            ImmutableMap.of(),
            new TestDependencyGraph(
                createDependency("ExampleClass"),
                ImmutableSet.of(COMMONS_IO_DEPENDENCY),
                ImmutableSet.of(COMMONS_LANG_DEPENDENCY),
                ImmutableSet.of(COMMONS_MATH_DEPENDENCY),
                ImmutableSet.of(JUNIT_DEPENDENCY, COMMONS_LOGGING_DEPENDENCY)));

    assertThat(analysis.getUsedDependencies())
        .containsExactlyInAnyOrder(
            new DebloatedDependency(
                COMMONS_IO_DEPENDENCY, ImmutableSet.of(COMMONS_LOGGING_DEPENDENCY)),
            new DebloatedDependency(
                COMMONS_LANG_DEPENDENCY, ImmutableSet.of(COMMONS_LOGGING_DEPENDENCY)),
            new DebloatedDependency(JUNIT_DEPENDENCY, ImmutableSet.of()));
  }

  @Test
  void dependenciesWithoutClassesThatBringOthersAreAggregators() {
    assertThat(analysisWithStarters().getAggregators())
        .containsOnly(
            // commons-io, commons-logging-api and commons-lang3, through the nested starter
            Map.entry(STARTER, new AggregatorUsage(3, 2)),
            Map.entry(NESTED_STARTER, new AggregatorUsage(2, 1)));
  }

  @Test
  void whatAnAggregatorBringsIsCountedFromTheResolvedDependencies() {
    // As in Maven, the children in the dependency tree come without their jar: their classes
    // must be read from the resolved dependencies they are equal to.
    final ProjectDependencyAnalysis analysis =
        new ProjectDependencyAnalysis(
            ImmutableSet.of(COMMONS_IO_DEPENDENCY),
            ImmutableSet.of(),
            ImmutableSet.of(),
            ImmutableSet.of(),
            ImmutableSet.of(STARTER),
            ImmutableSet.of(COMMONS_LANG_DEPENDENCY),
            ImmutableSet.of(),
            ImmutableSet.of(),
            ImmutableSet.of(),
            ImmutableMap.of(),
            new GraphWithChildren(
                ImmutableSet.of(COMMONS_IO_DEPENDENCY, STARTER),
                ImmutableMap.of(
                    STARTER,
                    ImmutableSet.of(
                        withoutJar(COMMONS_IO_DEPENDENCY), withoutJar(COMMONS_LANG_DEPENDENCY))),
                ImmutableSet.of(COMMONS_IO_DEPENDENCY, STARTER, COMMONS_LANG_DEPENDENCY)));

    assertThat(analysis.getAggregators())
        .containsOnly(Map.entry(STARTER, new AggregatorUsage(2, 1)));
  }

  private static Dependency withoutJar(Dependency dependency) {
    return new Dependency(
        dependency.getGroupId(),
        dependency.getDependencyId(),
        dependency.getVersion(),
        dependency.getScope(),
        null);
  }

  @Test
  void aggregatorsAreReportedButKeepTheirClassification() {
    final ProjectDependencyAnalysis analysis = analysisWithStarters();

    assertThat(analysis.getUnusedDirectDependencies()).contains(STARTER);
    assertThat(printed(analysis))
        .contains(
            "AGGREGATORS, DECLARED DEPENDENCIES WITHOUT CLASSES OF THEIR OWN [1]: ",
            "\t" + STARTER + ": 2 of 3 dependencies it brings are used")
        // the nested starter is not declared: its usage is part of the declared one's
        .noneMatch(line -> line.startsWith("\t" + NESTED_STARTER + ": "));
  }

  @Test
  void theAggregatorSectionIsOnlyPrintedWhenThereAreAggregators() {
    final ProjectDependencyAnalysis analysis =
        new ProjectDependencyAnalysis(
            ImmutableSet.of(COMMONS_IO_DEPENDENCY),
            ImmutableSet.of(),
            ImmutableSet.of(),
            ImmutableSet.of(),
            ImmutableSet.of(),
            ImmutableSet.of(),
            ImmutableSet.of(),
            ImmutableSet.of(),
            ImmutableSet.of(),
            ImmutableMap.of(),
            new GraphWithChildren(ImmutableSet.of(COMMONS_IO_DEPENDENCY), ImmutableMap.of()));

    assertThat(analysis.getAggregators()).isEmpty();
    assertThat(printed(analysis)).noneMatch(line -> line.startsWith("AGGREGATORS"));
  }

  /**
   * The project declares commons-io and a starter. The starter brings commons-io and a nested
   * starter, which brings commons-logging-api and commons-lang3. The project uses commons-io and
   * commons-logging-api.
   */
  private static ProjectDependencyAnalysis analysisWithStarters() {
    return new ProjectDependencyAnalysis(
        ImmutableSet.of(COMMONS_IO_DEPENDENCY),
        ImmutableSet.of(COMMONS_LOGGING_DEPENDENCY),
        ImmutableSet.of(),
        ImmutableSet.of(),
        ImmutableSet.of(STARTER),
        ImmutableSet.of(NESTED_STARTER, COMMONS_LANG_DEPENDENCY),
        ImmutableSet.of(),
        ImmutableSet.of(),
        ImmutableSet.of(),
        ImmutableMap.of(),
        new GraphWithChildren(
            ImmutableSet.of(COMMONS_IO_DEPENDENCY, STARTER),
            ImmutableMap.of(
                STARTER,
                ImmutableSet.of(COMMONS_IO_DEPENDENCY, NESTED_STARTER),
                NESTED_STARTER,
                ImmutableSet.of(COMMONS_LOGGING_DEPENDENCY, COMMONS_LANG_DEPENDENCY))));
  }

  private static List<String> printed(ProjectDependencyAnalysis analysis) {
    final PrintStream stdout = System.out;
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
      analysis.print();
    } finally {
      System.setOut(stdout);
    }
    return Arrays.asList(out.toString(StandardCharsets.UTF_8).split("\\R"));
  }

  /** A dependency graph given by the children of each dependency. */
  private static final class GraphWithChildren implements DependencyGraph {

    private final Set<Dependency> direct;
    private final Map<Dependency, Set<Dependency>> children;
    @Nullable private final Set<Dependency> resolved;

    GraphWithChildren(Set<Dependency> direct, Map<Dependency, Set<Dependency>> children) {
      this(direct, children, null);
    }

    /**
     * Creates a graph whose resolved dependencies, with their jars, are given apart from the
     * dependency tree, as in Maven.
     */
    GraphWithChildren(
        Set<Dependency> direct,
        Map<Dependency, Set<Dependency>> children,
        @Nullable Set<Dependency> resolved) {
      this.direct = direct;
      this.children = children;
      this.resolved = resolved;
    }

    @Override
    public Dependency projectCoordinates() {
      return createDependency("ExampleClass");
    }

    @Override
    public Set<Dependency> directDependencies() {
      return direct;
    }

    @Override
    public Set<Dependency> transitiveDependencies() {
      final ImmutableSet.Builder<Dependency> transitive = ImmutableSet.builder();
      children.values().forEach(transitive::addAll);
      return ImmutableSet.copyOf(
          transitive.build().stream().filter(d -> !direct.contains(d)).iterator());
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
      if (resolved != null) {
        return resolved;
      }
      return ImmutableSet.<Dependency>builder()
          .addAll(direct)
          .addAll(transitiveDependencies())
          .build();
    }

    @Override
    public Set<Dependency> getDependenciesForParent(Dependency parent) {
      return children.getOrDefault(parent, ImmutableSet.of());
    }
  }
}
