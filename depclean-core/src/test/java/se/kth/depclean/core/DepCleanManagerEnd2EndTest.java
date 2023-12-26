package se.kth.depclean.core;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.google.common.collect.ImmutableSet.of;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.kth.depclean.core.analysis.AnalysisFailureException;
import se.kth.depclean.core.analysis.graph.ClassMembersVisitorCounter;
import se.kth.depclean.core.analysis.graph.DefaultCallGraph;
import se.kth.depclean.core.analysis.model.ProjectDependencyAnalysis;
import se.kth.depclean.core.fake.depmanager.AllDependenciesUsedDependencyManager;
import se.kth.depclean.core.fake.depmanager.EmptyProjectDependencyManager;
import se.kth.depclean.core.fake.depmanager.FakeDependencyManager;
import se.kth.depclean.core.fake.depmanager.NoDependencyUsedDependencyManager;
import se.kth.depclean.core.fake.depmanager.OnlyDirectAndInheritedUsedDependencyManager;
import se.kth.depclean.core.fake.depmanager.OnlyDirectUsedDependencyManager;
import se.kth.depclean.core.model.Dependency;
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
        "[INFO] Skipping because packaging type is pom"
    );
  }

  @Test
  void shouldPassForEmptyProject() throws AnalysisFailureException {
    final DepCleanManager depCleanManager = new DepCleanManagerBuilder()
        .withDependencyManager(EmptyProjectDependencyManager.class).build();

    final ProjectDependencyAnalysis analysis = depCleanManager.execute();

    assertThat(analysis.getUsedDirectDependencies()).isEmpty();
    assertThat(analysis.getUsedInheritedDirectDependencies()).isEmpty();
    assertThat(analysis.getUsedTransitiveDependencies()).isEmpty();
    assertThat(analysis.getUnusedDirectDependencies()).isEmpty();
    assertThat(analysis.getUnusedInheritedDirectDependencies()).isEmpty();
    assertThat(analysis.getUnusedInheritedTransitiveDependencies()).isEmpty();
    assertThat(analysis.getUnusedTransitiveDependencies()).isEmpty();
  }

  @Test
  void shouldReportAllDependencyUsed() throws AnalysisFailureException {
    final DepCleanManager depCleanManager = new DepCleanManagerBuilder()
        .withDependencyManager(AllDependenciesUsedDependencyManager.class).build();

    final ProjectDependencyAnalysis analysis = depCleanManager.execute();

    assertThat(analysis.getUsedDirectDependencies()).hasSize(1);
    assertThat(analysis.getUsedInheritedDirectDependencies()).hasSize(1);
    assertThat(analysis.getUsedTransitiveDependencies()).hasSize(1);
    assertThat(analysis.getUnusedDirectDependencies()).isEmpty();
    assertThat(analysis.getUnusedInheritedDirectDependencies()).isEmpty();
    assertThat(analysis.getUnusedInheritedTransitiveDependencies()).isEmpty();
    assertThat(analysis.getUnusedTransitiveDependencies()).isEmpty();
  }

  @Test
  void shouldReportNoDependencyUsed() throws AnalysisFailureException {
    final DepCleanManager depCleanManager = new DepCleanManagerBuilder()
        .withDependencyManager(NoDependencyUsedDependencyManager.class).build();

    final ProjectDependencyAnalysis analysis = depCleanManager.execute();

    assertThat(analysis.getUsedDirectDependencies()).isEmpty();
    assertThat(analysis.getUsedInheritedDirectDependencies()).isEmpty();
    assertThat(analysis.getUsedTransitiveDependencies()).isEmpty();
    assertThat(analysis.getUnusedDirectDependencies()).hasSize(1);
    assertThat(analysis.getUnusedInheritedDirectDependencies()).hasSize(1);
    assertThat(analysis.getUnusedInheritedTransitiveDependencies()).hasSize(1);
    assertThat(analysis.getUnusedTransitiveDependencies()).hasSize(1);
  }

  @Test
  void shouldReportOnlyDirectAndInheritedDependenciesUsed() throws AnalysisFailureException {
    final DepCleanManager depCleanManager = new DepCleanManagerBuilder()
        .withDependencyManager(OnlyDirectAndInheritedUsedDependencyManager.class).build();

    final ProjectDependencyAnalysis analysis = depCleanManager.execute();

    assertThat(analysis.getUsedDirectDependencies()).hasSize(1);
    assertThat(analysis.getUsedInheritedDirectDependencies()).hasSize(1);
    assertThat(analysis.getUsedTransitiveDependencies()).isEmpty();
    assertThat(analysis.getUnusedDirectDependencies()).isEmpty();
    assertThat(analysis.getUnusedInheritedDirectDependencies()).isEmpty();
    assertThat(analysis.getUnusedInheritedTransitiveDependencies()).isEmpty();
    assertThat(analysis.getUnusedTransitiveDependencies()).hasSize(1);
  }

  @Test
  void shouldReportOnlyDirectDependencyUsed() throws AnalysisFailureException {
    final DepCleanManager depCleanManager = new DepCleanManagerBuilder()
        .withDependencyManager(OnlyDirectUsedDependencyManager.class).build();

    final ProjectDependencyAnalysis analysis = depCleanManager.execute();

    assertThat(analysis.getUsedDirectDependencies()).hasSize(1);
    assertThat(analysis.getUsedInheritedDirectDependencies()).isEmpty();
    assertThat(analysis.getUsedTransitiveDependencies()).isEmpty();
    assertThat(analysis.getUnusedDirectDependencies()).isEmpty();
    assertThat(analysis.getUnusedInheritedDirectDependencies()).hasSize(1);
    assertThat(analysis.getUnusedInheritedTransitiveDependencies()).hasSize(1);
    assertThat(analysis.getUnusedTransitiveDependencies()).hasSize(1);
  }

  @Test
  void shouldFailForUnusedDirectDependency() {
    final DepCleanManager depCleanManager = new DepCleanManagerBuilder()
        .withDependencyManager(NoDependencyUsedDependencyManager.class)
        .withFailIfUnusedDirectDependency()
        .build();

    assertThatThrownBy(depCleanManager::execute)
        .isInstanceOf(AnalysisFailureException.class)
        .hasMessage("Build failed due to unused direct dependencies in the dependency tree of the project.");
  }

  @Test
  void shouldFailForUnusedInheritedDependency() {
    final DepCleanManager depCleanManager = new DepCleanManagerBuilder()
        .withDependencyManager(OnlyDirectUsedDependencyManager.class)
        .withFailIfUnusedInheritedDependency()
        .build();

    assertThatThrownBy(depCleanManager::execute)
        .isInstanceOf(AnalysisFailureException.class)
        .hasMessage("Build failed due to unused inherited direct dependencies in the dependency tree of the project.");
  }

  @Test
  void shouldFailForUnusedTransitiveDependency() {
    final DepCleanManager depCleanManager = new DepCleanManagerBuilder()
        .withDependencyManager(OnlyDirectAndInheritedUsedDependencyManager.class)
        .withFailIfUnusedTransitiveDependency()
        .build();

    assertThatThrownBy(depCleanManager::execute)
        .isInstanceOf(AnalysisFailureException.class)
        .hasMessage("Build failed due to unused transitive dependencies in the dependency tree of the project.");
  }

  @Test
  void shouldIgnoreDependencies() throws AnalysisFailureException {
    final DepCleanManager depCleanManager = new DepCleanManagerBuilder()
            .withDependencyManager(OnlyDirectAndInheritedUsedDependencyManager.class)
            .withIgnoreDependencies(of("se.kth.depclean.core.test:commons-io:.*", "se.kth.depclean.core.test:commons-logging-api:.*"))
            .build();

    final ProjectDependencyAnalysis analysis = depCleanManager.execute();

    assertThat(analysis.getIgnoredDependencies()).hasSize(2);
    assertThat(analysis.getIgnoredDependencies()).contains(new Dependency("se.kth.depclean.core.test", "commons-io", "1.0.0", "compile", new File("")));
    assertThat(analysis.getIgnoredDependencies()).contains(new Dependency("se.kth.depclean.core.test", "commons-logging-api", "1.0.0", "compile", new File("")));
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
    private boolean failIfUnusedInheritedDirect = false;
    private boolean failIfUnusedInheritedTransitive= false;
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
          failIfUnusedInheritedDirect,
          failIfUnusedInheritedTransitive,
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
    public <T extends FakeDependencyManager> DepCleanManagerBuilder withDependencyManager(Class<T> clazz) {
      this.dependencyManager = clazz.getDeclaredConstructor(Logger.class).newInstance(logger);
      return this;
    }

    public DepCleanManagerBuilder withFailIfUnusedDirectDependency() {
      this.failIfUnusedDirect = true;
      return this;
    }

    public DepCleanManagerBuilder withFailIfUnusedInheritedDependency() {
      this.failIfUnusedInheritedDirect = true;
      return this;
    }

    public DepCleanManagerBuilder withFailIfUnusedTransitiveDependency() {
      this.failIfUnusedTransitive = true;
      return this;
    }

    public DepCleanManagerBuilder withIgnoreDependencies(Set<String> ignoreDependencies) {
      this.ignoreDependencies = ignoreDependencies;
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