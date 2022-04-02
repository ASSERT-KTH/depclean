package se.kth.depclean.core;


import static com.google.common.collect.ImmutableSet.of;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.kth.depclean.core.analysis.AnalysisFailureException;
import se.kth.depclean.core.analysis.DefaultProjectDependencyAnalyzer;
import se.kth.depclean.core.analysis.graph.ClassMembersVisitorCounter;
import se.kth.depclean.core.analysis.graph.DefaultCallGraph;
import se.kth.depclean.core.analysis.model.ProjectDependencyAnalysis;
import se.kth.depclean.core.fake.graph.AllDependenciesUsedDependencyGraph;
import se.kth.depclean.core.fake.graph.FakeDependencyGraph;
import se.kth.depclean.core.fake.FakeDependencyManager;
import se.kth.depclean.core.fake.graph.NoDependencyUsedDependencyGraph;
import se.kth.depclean.core.fake.graph.OnlyDirectAndInheritedUsedDependencyGraph;
import se.kth.depclean.core.fake.graph.OnlyDirectUsedDependencyGraph;
import se.kth.depclean.core.wrapper.DependencyManagerWrapper;

class DepCleanManagerEnd2EndTest {

  private static final TestAppender appender = new TestAppender();
  private static final Logger logger = Logger.getRootLogger();

  @BeforeEach
  public void setUp() {
    logger.addAppender(appender);
  }

  @AfterEach
  public void tearDown() {
    appender.clear();
    logger.removeAppender(appender);
    // Static classes are evils :)
    DefaultCallGraph.clear();
    ClassMembersVisitorCounter.resetClassCounters();
  }

  @Test
  void shouldBeSkipped() throws AnalysisFailureException {
    final DepCleanManager depCleanManager = new DepCleanManagerBuilder().skipDepClean().build();

    depCleanManager.execute();

    assertThat(logsAsString()).contains(
        "[INFO] Skipping DepClean plugin execution"
    );
  }

  @Test
  void shouldBeSkippedBecauseOfType() throws AnalysisFailureException {
    final DepCleanManager depCleanManager =
        new DepCleanManagerBuilder().withDependencyManager(
            new FakeDependencyManagerWithMavenPomType()
        ).build();

    depCleanManager.execute();

    assertThat(logsAsString()).contains(
        "[INFO] Skipping because packaging type pom"
    );
  }

  @Test
  void shouldPassForEmptyProject() throws AnalysisFailureException {
    final DepCleanManager depCleanManager = new DepCleanManagerBuilder().build();

    final ProjectDependencyAnalysis analysis = depCleanManager.execute();

    assertThat(analysis.getUsedDirectDependencies()).isEmpty();
    assertThat(analysis.getUsedInheritedDependencies()).isEmpty();
    assertThat(analysis.getUsedTransitiveDependencies()).isEmpty();
    assertThat(analysis.getUnusedDirectDependencies()).isEmpty();
    assertThat(analysis.getUnusedInheritedDependencies()).isEmpty();
    assertThat(analysis.getUnusedTransitiveDependencies()).isEmpty();
  }

  @Test
  void shouldReportAllDependencyUsed() throws AnalysisFailureException {
    final DepCleanManager depCleanManager = new DepCleanManagerBuilder()
        .withGraph(AllDependenciesUsedDependencyGraph.class).build();

    final ProjectDependencyAnalysis analysis = depCleanManager.execute();

    assertThat(analysis.getUsedDirectDependencies()).hasSize(1);
    assertThat(analysis.getUsedInheritedDependencies()).hasSize(1);
    assertThat(analysis.getUsedTransitiveDependencies()).hasSize(1);
    assertThat(analysis.getUnusedDirectDependencies()).isEmpty();
    assertThat(analysis.getUnusedInheritedDependencies()).isEmpty();
    assertThat(analysis.getUnusedTransitiveDependencies()).isEmpty();
  }

  @Test
  void shouldReportNoDependencyUsed() throws AnalysisFailureException {
    final DepCleanManager depCleanManager = new DepCleanManagerBuilder()
        .withGraph(NoDependencyUsedDependencyGraph.class).build();

    final ProjectDependencyAnalysis analysis = depCleanManager.execute();

    assertThat(analysis.getUsedDirectDependencies()).isEmpty();
    assertThat(analysis.getUsedInheritedDependencies()).isEmpty();
    assertThat(analysis.getUsedTransitiveDependencies()).isEmpty();
    assertThat(analysis.getUnusedDirectDependencies()).hasSize(1);
    assertThat(analysis.getUnusedInheritedDependencies()).hasSize(1);
    assertThat(analysis.getUnusedTransitiveDependencies()).hasSize(1);
  }

  @Test
  void shouldReportOnlyDirectAndInheritedDependenciesUsed() throws AnalysisFailureException {
    final DepCleanManager depCleanManager = new DepCleanManagerBuilder()
        .withGraph(OnlyDirectAndInheritedUsedDependencyGraph.class).build();

    final ProjectDependencyAnalysis analysis = depCleanManager.execute();

    assertThat(analysis.getUsedDirectDependencies()).hasSize(1);
    assertThat(analysis.getUsedInheritedDependencies()).hasSize(1);
    assertThat(analysis.getUsedTransitiveDependencies()).isEmpty();
    assertThat(analysis.getUnusedDirectDependencies()).isEmpty();
    assertThat(analysis.getUnusedInheritedDependencies()).isEmpty();
    assertThat(analysis.getUnusedTransitiveDependencies()).hasSize(1);
  }

  @Test
  void shouldReportOnlyDirectDependencyUsed() throws AnalysisFailureException {
    final DepCleanManager depCleanManager = new DepCleanManagerBuilder()
        .withGraph(OnlyDirectUsedDependencyGraph.class).build();

    final ProjectDependencyAnalysis analysis = depCleanManager.execute();

    assertThat(analysis.getUsedDirectDependencies()).hasSize(1);
    assertThat(analysis.getUsedInheritedDependencies()).isEmpty();
    assertThat(analysis.getUsedTransitiveDependencies()).isEmpty();
    assertThat(analysis.getUnusedDirectDependencies()).isEmpty();
    assertThat(analysis.getUnusedInheritedDependencies()).hasSize(1);
    assertThat(analysis.getUnusedTransitiveDependencies()).hasSize(1);
  }

  @Test
  void shouldFailForUnusedDirectDependency() {
    final DepCleanManager depCleanManager = new DepCleanManagerBuilder()
        .withGraph(NoDependencyUsedDependencyGraph.class)
        .withFailIfUnusedDirectDependency()
        .build();

    assertThatThrownBy(depCleanManager::execute)
        .isInstanceOf(AnalysisFailureException.class)
        .hasMessage("Build failed due to unused direct dependencies in the dependency tree of the project.");
  }

  @Test
  void shouldFailForUnusedInheritedDependency() {
    final DepCleanManager depCleanManager = new DepCleanManagerBuilder()
        .withGraph(OnlyDirectUsedDependencyGraph.class)
        .withFailIfUnusedInheritedDependency()
        .build();

    assertThatThrownBy(depCleanManager::execute)
        .isInstanceOf(AnalysisFailureException.class)
        .hasMessage("Build failed due to unused inherited dependencies in the dependency tree of the project.");
  }

  @Test
  void shouldFailForUnusedTransitiveDependency() {
    final DepCleanManager depCleanManager = new DepCleanManagerBuilder()
        .withGraph(OnlyDirectAndInheritedUsedDependencyGraph.class)
        .withFailIfUnusedTransitiveDependency()
        .build();

    assertThatThrownBy(depCleanManager::execute)
        .isInstanceOf(AnalysisFailureException.class)
        .hasMessage("Build failed due to unused transitive dependencies in the dependency tree of the project.");
  }

  private String logsAsString() {
    return appender.getLog().stream()
        .map(this::toString)
        .collect(Collectors.joining("\n"));
  }

  private String toString(LoggingEvent le) {
    return String.format("[%s] %s%n", le.getLevel(), le.getRenderedMessage());
  }

  // ---- HELPER CLASSES ----

  static class TestAppender extends AppenderSkeleton {
    private final List<LoggingEvent> log = new ArrayList<>();

    @Override
    public boolean requiresLayout() {
      return false;
    }

    @Override
    protected void append(final LoggingEvent loggingEvent) {
      log.add(loggingEvent);
      System.out.printf("[%s] %s%n", loggingEvent.getLevel(), loggingEvent.getRenderedMessage());
    }

    @Override
    public void close() {
    }

    public List<LoggingEvent> getLog() {
      return new ArrayList<>(log);
    }

    public void clear() {
      log.clear();
    }
  }

  static class DepCleanManagerBuilder {
    private DependencyManagerWrapper dependencyManager = new FakeDependencyManager(logger);
    private boolean skipDepClean = false;
    private boolean ignoreTests = false;
    private Set<String> ignoreScopes = of();
    private Set<String> ignoreDependencies = of();
    private boolean failIfUnusedDirect = false;
    private boolean failIfUnusedTransitive = false;
    private boolean failIfUnusedInherited = false;
    private boolean createPomDebloated = false;
    private boolean createResultJson = false;
    private boolean createClassUsageCsv = false;

    public DepCleanManager build() {
      return new DepCleanManager(
          dependencyManager,
          skipDepClean,
          ignoreTests,
          ignoreScopes,
          ignoreDependencies,
          failIfUnusedDirect,
          failIfUnusedTransitive,
          failIfUnusedInherited,
          createPomDebloated,
          createResultJson,
          createClassUsageCsv
      );
    }

    public DepCleanManagerBuilder skipDepClean() {
      this.skipDepClean = true;
      return this;
    }

    public DepCleanManagerBuilder withDependencyManager(DependencyManagerWrapper dependencyManager) {
      this.dependencyManager = dependencyManager;
      return this;
    }

    @SneakyThrows
    public <T extends FakeDependencyGraph> DepCleanManagerBuilder withGraph(Class<T> clazz) {
      this.dependencyManager = new FakeDependencyManager(logger, clazz.getDeclaredConstructor().newInstance());
      return this;
    }

    public DepCleanManagerBuilder withFailIfUnusedDirectDependency() {
      this.failIfUnusedDirect = true;
      return this;
    }

    public DepCleanManagerBuilder withFailIfUnusedInheritedDependency() {
      this.failIfUnusedInherited = true;
      return this;
    }

    public DepCleanManagerBuilder withFailIfUnusedTransitiveDependency() {
      this.failIfUnusedTransitive = true;
      return this;
    }
  }

  static class FakeDependencyManagerWithMavenPomType extends FakeDependencyManager {

    public FakeDependencyManagerWithMavenPomType() {
      super(logger);
    }

    @Override
    public boolean isMaven() {
      return true;
    }

    @Override
    public boolean isPackagingPom() {
      return true;
    }
  }

}