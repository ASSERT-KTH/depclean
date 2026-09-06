/*
 * Copyright (c) 2020, CASTOR Software Research Centre (www.castor.kth.se)
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package se.kth.depclean;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import se.kth.depclean.core.DepCleanManager;
import se.kth.depclean.core.analysis.AnalysisFailureException;
import se.kth.depclean.core.analysis.model.ProjectDependencyAnalysis;
import se.kth.depclean.report.AnalysisInputs;
import se.kth.depclean.report.AnalysisSnapshot;
import se.kth.depclean.report.AnalysisSnapshotFile;
import se.kth.depclean.report.DepCleanReportRenderer;
import se.kth.depclean.wrapper.MavenDependencyManager;

/**
 * Generates the DepClean page of the Maven site ({@code target/site/depclean.html}), listing the
 * used and potentially unused dependencies of the project. The report is read-only: it never
 * rewrites the POM nor fails the build. When {@code depclean:depclean} already ran in the same
 * build, its result is reused instead of analysing the project a second time.
 */
@Mojo(
    name = "report",
    requiresDependencyCollection = ResolutionScope.TEST,
    requiresDependencyResolution = ResolutionScope.TEST,
    threadSafe = true)
@Execute(phase = LifecyclePhase.TEST_COMPILE)
public class DepCleanReportMojo extends AbstractMavenReport {

  /** The Maven session. */
  @Parameter(defaultValue = "${session}", readonly = true)
  @SuppressWarnings("NullAway") // Injected by Maven
  private MavenSession session;

  /**
   * Add a list of regular expressions matching dependencies to be ignored by DepClean during the
   * analysis and considered as used dependencies. Each pattern is matched (case-insensitively)
   * against the whole <code>groupId:artifactId:version:scope</code> coordinate.
   */
  @Parameter(property = "ignoreDependencies")
  @SuppressWarnings("NullAway") // Injected by Maven
  private Set<String> ignoreDependencies;

  /** Ignore dependencies with specific scopes from the DepClean analysis. */
  @Parameter(property = "ignoreScopes")
  @SuppressWarnings("NullAway") // Injected by Maven
  private Set<String> ignoreScopes;

  /**
   * If this is true, DepClean will not analyze the test sources in the project, and, therefore, the
   * dependencies that are only used for testing will be considered unused.
   */
  @Parameter(property = "ignoreTests", defaultValue = "false")
  private boolean ignoreTests;

  /** Skip the report completely. */
  @Parameter(property = "skipDepClean", defaultValue = "false")
  private boolean skipDepClean;

  /** To build the dependency graph. */
  @Inject
  @SuppressWarnings("NullAway") // Injected by Maven
  private DependencyGraphBuilder dependencyGraphBuilder;

  @Override
  public String getOutputName() {
    return "depclean";
  }

  @Override
  public String getName(Locale locale) {
    return "DepClean";
  }

  @Override
  public String getDescription(Locale locale) {
    return "Used and potentially unused dependencies detected by DepClean's bytecode analysis.";
  }

  @Override
  public boolean canGenerateReport() throws MavenReportException {
    if (skipDepClean) {
      getLog().info("Skipping DepClean report");
      return false;
    }
    if ("pom".equals(project.getPackaging())) {
      getLog().info("Skipping DepClean report because packaging type is pom");
      return false;
    }
    return super.canGenerateReport();
  }

  @Override
  protected void executeReport(Locale locale) throws MavenReportException {
    AnalysisSnapshot.Settings settings =
        new AnalysisSnapshot.Settings(ignoreTests, ignoreScopes, ignoreDependencies);
    AnalysisSnapshotFile snapshotFile =
        new AnalysisSnapshotFile(Paths.get(project.getBuild().getDirectory()));
    try {
      String inputsFingerprint =
          AnalysisInputs.fingerprint(project.getFile().toPath(), classDirectories());
      Optional<AnalysisSnapshot> stored = snapshotFile.readIfFresh(settings, inputsFingerprint);
      AnalysisSnapshot snapshot;
      if (stored.isPresent()) {
        getLog().info("Reusing the DepClean analysis from " + snapshotFile.getPath());
        snapshot = stored.get();
      } else {
        snapshot = analyze(settings, inputsFingerprint);
        snapshotFile.write(snapshot);
      }
      new DepCleanReportRenderer(getSink(), snapshot, project.getName(), stored.isPresent())
          .render();
    } catch (AnalysisFailureException | IOException e) {
      throw new MavenReportException("Unable to generate the DepClean report", e);
    }
  }

  private AnalysisSnapshot analyze(AnalysisSnapshot.Settings settings, String inputsFingerprint)
      throws AnalysisFailureException, IOException, MavenReportException {
    ProjectDependencyAnalysis analysis =
        new DepCleanManager(
                new MavenDependencyManager(getLog(), project, session, dependencyGraphBuilder),
                false,
                ignoreTests,
                ignoreScopes,
                ignoreDependencies,
                false,
                false,
                false,
                false,
                false,
                false,
                false)
            .execute();
    if (analysis == null) {
      throw new MavenReportException("DepClean did not analyse the project");
    }
    return AnalysisSnapshot.from(analysis, settings, inputsFingerprint);
  }

  private List<Path> classDirectories() {
    return Arrays.asList(
        Paths.get(project.getBuild().getOutputDirectory()),
        Paths.get(project.getBuild().getTestOutputDirectory()));
  }
}
