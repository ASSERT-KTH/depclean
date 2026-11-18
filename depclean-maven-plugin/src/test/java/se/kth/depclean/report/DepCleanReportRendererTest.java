package se.kth.depclean.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.maven.doxia.module.xhtml5.Xhtml5SinkFactory;
import org.apache.maven.doxia.sink.Sink;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link DepCleanReportRenderer}. */
class DepCleanReportRendererTest {

  private static final AnalysisSnapshot.Settings SETTINGS =
      new AnalysisSnapshot.Settings(
          true, Collections.singleton("provided"), Collections.singleton("com.google.guava.*"));

  @Test
  void rendersSummaryCategoriesAndDetails() throws IOException {
    String html = render(AnalysisSnapshotFileTest.snapshot(SETTINGS), "foobar", true);

    assertThat(html)
        .contains("<title>DepClean Report</title>")
        .contains("analyses the bytecode of foobar")
        .contains("reused from the depclean:depclean run")
        .contains("provided")
        .contains("com.google.guava.*")
        // summary links to the category sections
        .contains("<a href=\"#unused-transitive\">Potentially unused transitive dependencies</a>")
        .contains("<a id=\"used-direct\"></a>")
        .contains("Used direct dependencies (1)")
        .contains("Potentially unused transitive dependencies (1)")
        .contains("Potentially unused direct dependencies (1)")
        .contains("<td>commons-io</td>")
        .contains("<td>2.22.0</td>")
        .contains("<td>compile</td>")
        .contains("<td>593 KB</td>")
        // a missing scope renders as a dash instead of "null"
        .contains("<td>-</td>")
        .doesNotContain("null")
        // used-class ratio links to the per-dependency details
        .contains("<a href=\"#dep-commons-io_commons-io_2.22.0\">2 / 300</a>")
        .contains("<a id=\"dep-commons-io_commons-io_2.22.0\"></a>")
        .contains("<code>org.apache.commons.io.FileUtils</code>")
        .contains("<code>org.apache.commons.io.IOUtils</code>")
        // unused dependencies have no details section
        .doesNotContain("dep-commons-codec");
    // sections appear in the documented order
    assertThat(html.indexOf("Summary"))
        .isLessThan(html.indexOf("Used direct dependencies (1)"))
        .isLessThan(html.indexOf("Used only by the tests (1)"))
        .isLessThan(html.indexOf("Aggregators (1)"))
        .isLessThan(html.indexOf("Used classes per dependency"));
  }

  @Test
  void saysWhyADependencyIsUsed() throws IOException {
    String html = render(AnalysisSnapshotFileTest.snapshot(SETTINGS), "foobar", true);

    assertThat(html)
        // the notes column summarizes, and flags what only the tests use
        .contains(
            "<td>referenced by 1 main class; referenced by 1 test class;"
                + " only the tests use it</td>")
        // the details list the evidence
        .contains("Referenced by the main code: <code>org.foo.App</code>")
        .contains("Referenced by the tests: <code>org.foo.AppTest</code>");
  }

  @Test
  void listsTheDependenciesOnlyTheTestsUse() throws IOException {
    String html = render(AnalysisSnapshotFileTest.snapshot(SETTINGS), "foobar", true);

    assertThat(html)
        .contains("<a href=\"#test-only\">Used only by the tests</a>")
        .contains("<a id=\"test-only\"></a>")
        .contains("can be declared with the test scope");
  }

  @Test
  void listsTheAggregatorsWithHowMuchOfWhatTheyBringIsUsed() throws IOException {
    String html = render(AnalysisSnapshotFileTest.snapshot(SETTINGS), "foobar", true);

    assertThat(html)
        .contains("<a href=\"#aggregators\">Aggregators</a>")
        .contains("<td>aggregator: 21 of 24 dependencies it brings are used</td>")
        .contains("<td>spring-boot-starter-web</td>")
        .contains("<td>21 / 24</td>")
        // the category the aggregator is in links to its section
        .contains("<a href=\"#unused-direct\">Potentially unused direct dependencies</a></td>");
  }

  @Test
  void listsAtMostTwentyClassesPerKindOfEvidence() throws IOException {
    List<String> classes = new ArrayList<>();
    for (int i = 0; i < 25; i++) {
      classes.add(String.format("org.foo.C%02d", i));
    }
    AnalysisSnapshot.Entry used =
        new AnalysisSnapshot.Entry(
            "commons-io",
            "commons-io",
            "2.22.0",
            "compile",
            608_000L,
            300,
            Collections.singletonList("org.apache.commons.io.IOUtils"),
            new AnalysisSnapshot.Usage(
                classes,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                false),
            null);
    AnalysisSnapshot snapshot =
        new AnalysisSnapshot(
            SETTINGS,
            "fingerprint",
            Collections.singletonList(used),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList());

    String html = render(snapshot, "foobar", false);

    assertThat(html).contains("<code>org.foo.C19</code> and 5 more").doesNotContain("org.foo.C20");
  }

  @Test
  void rendersEmptyAnalysis() throws IOException {
    AnalysisSnapshot empty =
        new AnalysisSnapshot(
            new AnalysisSnapshot.Settings(false, Collections.emptySet(), Collections.emptySet()),
            "fingerprint",
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList());

    String html = render(empty, "empty", false);

    assertThat(html)
        .contains("computed by this report")
        .contains("<td>none</td>")
        .contains("None.")
        .contains("No dependency is used by the project.");
  }

  private static String render(AnalysisSnapshot snapshot, String projectName, boolean reused)
      throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Sink sink = new Xhtml5SinkFactory().createSink(out, StandardCharsets.UTF_8.name());
    new DepCleanReportRenderer(sink, snapshot, projectName, reused).render();
    return out.toString(StandardCharsets.UTF_8.name());
  }
}
