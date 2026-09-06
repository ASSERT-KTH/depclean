package se.kth.depclean.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
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
        .contains("Potentially unused direct dependencies (0)")
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
        .contains("<code>org.apache.commons.io.IOUtils</code>");
    // unused dependencies have no details section
    assertThat(html).doesNotContain("dep-commons-codec");
    // sections appear in the documented order
    assertThat(html.indexOf("Summary"))
        .isLessThan(html.indexOf("Used direct dependencies (1)"))
        .isLessThan(html.indexOf("Used classes per dependency"));
  }

  @Test
  void rendersEmptyAnalysis() throws IOException {
    AnalysisSnapshot empty =
        new AnalysisSnapshot(
            new AnalysisSnapshot.Settings(false, Collections.emptySet(), Collections.emptySet()),
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
