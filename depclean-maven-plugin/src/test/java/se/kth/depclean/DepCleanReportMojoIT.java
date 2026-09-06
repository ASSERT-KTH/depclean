package se.kth.depclean;

import static com.soebes.itf.extension.assertj.MavenITAssertions.assertThat;

import com.soebes.itf.jupiter.extension.MavenGoal;
import com.soebes.itf.jupiter.extension.MavenJupiterExtension;
import com.soebes.itf.jupiter.extension.MavenTest;
import com.soebes.itf.jupiter.maven.MavenExecutionResult;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.assertj.core.api.ListAssert;

/**
 * Integration tests for {@link DepCleanReportMojo}. The projects used for testing are in
 * src/test/resources-its/se/kth/depclean/DepCleanReportMojoIT.
 */
@MavenJupiterExtension
public class DepCleanReportMojoIT {

  private static final String MAVEN_4_STDOUT_PREFIX = "[INFO] [stdout] ";

  @MavenTest
  @MavenGoal("site")
  void report_via_site(MavenExecutionResult result) throws IOException {
    assertThat(result).isSuccessful();
    Path site = targetDirectory(result).resolve("site");

    String report = read(site.resolve("depclean.html"));
    assertThat(report)
        .contains("DepClean Report")
        .contains("Used direct dependencies (1)")
        .contains("<td>commons-io</td>")
        .contains("Potentially unused direct dependencies (1)")
        .contains("<td>commons-compress</td>")
        .contains("Potentially unused transitive dependencies (2)")
        .contains("<td>commons-lang3</td>")
        .contains("<td>commons-codec</td>")
        .contains("<code>org.apache.commons.io.FileUtils</code>")
        .contains("computed by this report");

    // The page is listed in the "Project Reports" section of the site
    assertThat(read(site.resolve("project-reports.html")))
        .contains("depclean.html")
        .contains("DepClean");

    // The report never touches the project or writes the depclean:depclean artifacts
    Path project = targetDirectory(result).getParent();
    assertThat(project.resolve("pom-debloated.xml")).doesNotExist();
    assertThat(targetDirectory(result).resolve("depclean-results.json"))
        .doesNotExist();
    assertThat(targetDirectory(result).resolve("depclean-analysis.json"))
        .exists();
  }

  @MavenTest
  @MavenGoal("package")
  @MavenGoal("depclean:report")
  void report_reuses_analysis(MavenExecutionResult result) throws IOException {
    assertThatStdout(result)
        .anyMatch(line -> line.contains("Analysis snapshot written to"))
        .anyMatch(line -> line.contains("Reusing the DepClean analysis from"))
        // the DepClean banner is printed exactly once: by depclean:depclean, not by the report
        .filteredOn(line -> line.contains("D E P C L E A N   A N A L Y S I S   R E S U L T S"))
        .hasSize(1);

    String report = read(targetDirectory(result).resolve("reports").resolve("depclean.html"));
    assertThat(report)
        .contains("reused from the depclean:depclean run")
        .contains("<td>commons-compress</td>");
  }

  @MavenTest
  @MavenGoal("depclean:report")
  void report_recomputes_when_stale(MavenExecutionResult result) throws IOException {
    assertThatStdout(result)
        .noneMatch(line -> line.contains("Reusing the DepClean analysis from"))
        .contains(
            "USED DIRECT DEPENDENCIES [1]: ",
            "POTENTIALLY UNUSED DIRECT DEPENDENCIES [1]: ",
            "POTENTIALLY UNUSED TRANSITIVE DEPENDENCIES [2]: ");

    String report = read(targetDirectory(result).resolve("reports").resolve("depclean.html"));
    assertThat(report)
        .contains("computed by this report")
        .contains("<td>commons-compress</td>");
  }

  private static Path targetDirectory(MavenExecutionResult result) {
    File project = result.getMavenProjectResult().getTargetProjectDirectory().toFile();
    return project.toPath().resolve("target");
  }

  private static String read(Path file) throws IOException {
    assertThat(file).isRegularFile();
    return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
  }

  /** Asserts that the build succeeded and returns its standard output, normalized across Maven versions. */
  private static ListAssert<String> assertThatStdout(MavenExecutionResult result) {
    assertThat(result).isSuccessful();
    try {
      List<String> lines =
          Files.readAllLines(result.getMavenLog().getStdout(), StandardCharsets.UTF_8).stream()
              .map(DepCleanReportMojoIT::withoutStdoutPrefix)
              .collect(Collectors.toList());
      return assertThat(lines);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String withoutStdoutPrefix(String line) {
    return line.startsWith(MAVEN_4_STDOUT_PREFIX)
        ? line.substring(MAVEN_4_STDOUT_PREFIX.length())
        : line;
  }
}
