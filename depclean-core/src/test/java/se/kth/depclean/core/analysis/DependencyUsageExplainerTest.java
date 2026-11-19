package se.kth.depclean.core.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static se.kth.depclean.core.analysis.ProjectContextCreator.createDependency;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import se.kth.depclean.core.analysis.graph.ClassUsageGraph;
import se.kth.depclean.core.analysis.model.DependencyUsage;
import se.kth.depclean.core.model.ClassName;
import se.kth.depclean.core.model.Dependency;
import se.kth.depclean.core.model.ExtraClassSource;
import se.kth.depclean.core.model.ProjectContext;

class DependencyUsageExplainerTest implements ProjectContextCreator {

  private static final String MAIN = "com.example.Main";
  private static final String MAIN_TEST = "com.example.MainTest";
  private static final String TEST_ENGINE = "org.junit.platform.engine.TestEngine";

  /**
   * The main code and the tests use commons-io, which uses commons-logging-api; junit-jupiter
   * provides the test engine; commons-lang3 is only named in an XML resource and imported.
   */
  private static final ClassUsageGraph GRAPH =
      new ClassUsageGraph(
          ImmutableSet.of(MAIN, MAIN_TEST),
          ImmutableSet.of(MAIN, COMMONS_LANG_CLASS.getValue()),
          ImmutableSet.of(MAIN, MAIN_TEST, COMMONS_LANG_CLASS.getValue(), TEST_ENGINE),
          ImmutableMap.of(
              MAIN,
              ImmutableSet.of(COMMONS_IO_CLASS.getValue(), "java.lang.Object"),
              MAIN_TEST,
              ImmutableSet.of(COMMONS_IO_CLASS.getValue()),
              COMMONS_IO_CLASS.getValue(),
              ImmutableSet.of(COMMONS_LOGGING_CLASS.getValue(), COMMONS_IO_CLASS.getValue()),
              TEST_ENGINE,
              ImmutableSet.of(JUNIT_CLASS.getValue())),
          ImmutableMap.of(TEST_ENGINE, ImmutableSet.of(JUNIT_CLASS.getValue())));

  private final ProjectContext context =
      new ProjectContext(
          new TestDependencyGraph(
              createDependency("ExampleClass"),
              ImmutableSet.of(COMMONS_IO_DEPENDENCY, JUNIT_DEPENDENCY),
              ImmutableSet.of(COMMONS_LANG_DEPENDENCY),
              ImmutableSet.of(COMMONS_MATH_DEPENDENCY),
              ImmutableSet.of(COMMONS_LOGGING_DEPENDENCY)),
          ImmutableSet.of(Paths.get("target/classes")),
          ImmutableSet.of(Paths.get("target/test-classes")),
          Collections.emptySet(),
          ImmutableSet.of(COMMONS_MATH_DEPENDENCY),
          ImmutableSet.of(COMMONS_LANG_CLASS),
          Collections.emptySet(),
          ImmutableMap.of(
              COMMONS_LANG_CLASS,
              ImmutableSet.of(ExtraClassSource.XML_RESOURCE, ExtraClassSource.IMPORT),
              new ClassName("com.unknown.Processor"),
              ImmutableSet.of(ExtraClassSource.ANNOTATION_PROCESSOR)));

  private final Map<Dependency, DependencyUsage> usages =
      DependencyUsageExplainer.explain(
          context,
          GRAPH,
          ImmutableSet.of(
              COMMONS_IO_DEPENDENCY,
              COMMONS_LOGGING_DEPENDENCY,
              COMMONS_LANG_DEPENDENCY,
              JUNIT_DEPENDENCY,
              COMMONS_MATH_DEPENDENCY),
          ImmutableSet.of(COMMONS_MATH_DEPENDENCY));

  @Test
  void projectClassesReferencingADependencyAreSplitBetweenMainAndTests() {
    assertThat(usages.get(COMMONS_IO_DEPENDENCY))
        .isEqualTo(DependencyUsage.builder().mainClass(MAIN).testClass(MAIN_TEST).build());
  }

  @Test
  void aDependencyReachedThroughAnotherOneNamesIt() {
    assertThat(usages.get(COMMONS_LOGGING_DEPENDENCY))
        .isEqualTo(
            DependencyUsage.builder()
                .dependency("se.kth.depclean.core.analysis:commons-io:1.0.0")
                .build());
  }

  @Test
  void aServiceProviderNamesTheServiceType() {
    assertThat(usages.get(JUNIT_DEPENDENCY))
        .isEqualTo(DependencyUsage.builder().service(TEST_ENGINE).build());
  }

  @Test
  void classesFoundOutsideTheBytecodeKeepWhereTheyWereFound() {
    assertThat(usages.get(COMMONS_LANG_DEPENDENCY))
        .isEqualTo(
            DependencyUsage.builder()
                .xmlResourceClass(COMMONS_LANG_CLASS.getValue())
                .importedClass(COMMONS_LANG_CLASS.getValue())
                .build());
  }

  @Test
  void anIgnoredDependencyIsUsedWithoutEvidence() {
    assertThat(usages.get(COMMONS_MATH_DEPENDENCY))
        .isEqualTo(DependencyUsage.builder().ignored().build());
  }

  @Test
  void onlyUsedDependenciesAreExplained() {
    assertThat(
            DependencyUsageExplainer.explain(
                context, GRAPH, ImmutableSet.of(COMMONS_IO_DEPENDENCY), ImmutableSet.of()))
        .containsOnlyKeys(COMMONS_IO_DEPENDENCY);
  }
}
