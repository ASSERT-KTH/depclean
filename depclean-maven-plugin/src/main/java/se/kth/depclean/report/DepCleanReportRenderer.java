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

package se.kth.depclean.report;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.reporting.AbstractMavenReportRenderer;
import se.kth.depclean.report.AnalysisSnapshot.Entry;

/** Renders an {@link AnalysisSnapshot} as a Doxia document, i.e. the body of the DepClean site page. */
public final class DepCleanReportRenderer extends AbstractMavenReportRenderer {

  private static final String[] DEPENDENCY_TABLE_HEADER = {
    "Group ID", "Artifact ID", "Version", "Scope", "Size", "Used classes"
  };

  private final AnalysisSnapshot snapshot;
  private final String projectName;
  private final boolean reused;
  private final List<Category> categories;

  /**
   * Creates the renderer.
   *
   * @param sink the sink to render into
   * @param snapshot the analysis result to render
   * @param projectName the name (or coordinates) of the analysed project
   * @param reused whether the result was reused from an earlier {@code depclean:depclean} run
   */
  public DepCleanReportRenderer(
      Sink sink, AnalysisSnapshot snapshot, String projectName, boolean reused) {
    super(sink);
    this.snapshot = snapshot;
    this.projectName = projectName;
    this.reused = reused;
    this.categories =
        Arrays.asList(
            new Category("Used direct dependencies", "used-direct", snapshot.getUsedDirect()),
            new Category(
                "Used transitive dependencies", "used-transitive", snapshot.getUsedTransitive()),
            new Category(
                "Used inherited direct dependencies",
                "used-inherited-direct",
                snapshot.getUsedInheritedDirect()),
            new Category(
                "Used inherited transitive dependencies",
                "used-inherited-transitive",
                snapshot.getUsedInheritedTransitive()),
            new Category(
                "Potentially unused direct dependencies",
                "unused-direct",
                snapshot.getUnusedDirect()),
            new Category(
                "Potentially unused transitive dependencies",
                "unused-transitive",
                snapshot.getUnusedTransitive()),
            new Category(
                "Potentially unused inherited direct dependencies",
                "unused-inherited-direct",
                snapshot.getUnusedInheritedDirect()),
            new Category(
                "Potentially unused inherited transitive dependencies",
                "unused-inherited-transitive",
                snapshot.getUnusedInheritedTransitive()),
            new Category("Ignored dependencies", "ignored", snapshot.getIgnored()));
  }

  @Override
  public String getTitle() {
    return "DepClean Report";
  }

  @Override
  protected void renderBody() {
    startSection(getTitle(), "depclean");
    renderIntroduction();
    renderSummary();
    for (Category category : categories) {
      renderCategory(category);
    }
    renderDetails();
    endSection();
  }

  private void renderIntroduction() {
    paragraph(
        "DepClean statically analyses the bytecode of "
            + projectName
            + " to detect the dependencies whose classes are never referenced by the project. Such"
            + " dependencies are potentially unused: they are candidates for removal (direct"
            + " dependencies) or exclusion (transitive dependencies).");
    AnalysisSnapshot.Settings settings = snapshot.getSettings();
    startTable();
    tableRow(new String[] {"Test classes analysed", settings.isIgnoreTests() ? "no" : "yes"});
    tableRow(new String[] {"Ignored scopes", joinOrNone(settings.getIgnoreScopes())});
    tableRow(new String[] {"Ignored dependencies", joinOrNone(settings.getIgnoreDependencies())});
    tableRow(
        new String[] {
          "Analysis result",
          reused ? "reused from the depclean:depclean run of this build" : "computed by this report"
        });
    endTable();
  }

  private void renderSummary() {
    startSection("Summary", "summary");
    startTable();
    tableHeader(new String[] {"Category", "Dependencies", "Total size"});
    for (Category category : categories) {
      tableRow(
          new String[] {
            createLinkPatternedText(category.title, "#" + category.anchor),
            String.valueOf(category.entries.size()),
            formatSize(category.entries.stream().mapToLong(Entry::getSizeBytes).sum())
          });
    }
    endTable();
    endSection();
  }

  private void renderCategory(Category category) {
    startSection(category.title + " (" + category.entries.size() + ")", category.anchor);
    if (category.entries.isEmpty()) {
      paragraph("None.");
    } else {
      startTable();
      tableHeader(DEPENDENCY_TABLE_HEADER);
      for (Entry entry : category.entries) {
        tableRow(
            new String[] {
              entry.getGroupId(),
              entry.getArtifactId(),
              entry.getVersion(),
              entry.getScope(),
              formatSize(entry.getSizeBytes()),
              usedClassesCell(entry)
            });
      }
      endTable();
    }
    endSection();
  }

  private String usedClassesCell(Entry entry) {
    String ratio = entry.getUsedClasses().size() + " / " + entry.getTotalClasses();
    return entry.getUsedClasses().isEmpty()
        ? ratio
        : createLinkPatternedText(ratio, "#" + detailsAnchor(entry));
  }

  private void renderDetails() {
    List<Entry> usedEntries = new ArrayList<>();
    for (Category category : categories) {
      if (category.anchor.startsWith("used-")) {
        usedEntries.addAll(category.entries);
      }
    }
    startSection("Used classes per dependency", "details");
    if (usedEntries.isEmpty()) {
      paragraph("No dependency is used by the project.");
    } else {
      paragraph("The classes of each used dependency that the project references.");
      for (Entry entry : usedEntries) {
        renderDetailsOf(entry);
      }
    }
    endSection();
  }

  private void renderDetailsOf(Entry entry) {
    startSection(entry.coordinate(), detailsAnchor(entry));
    paragraph(
        entry.getUsedClasses().size()
            + " of "
            + entry.getTotalClasses()
            + " classes used, "
            + formatSize(entry.getSizeBytes())
            + (entry.getScope() == null ? "" : ", scope " + entry.getScope()));
    if (!entry.getUsedClasses().isEmpty()) {
      sink.list();
      for (String className : entry.getUsedClasses()) {
        sink.listItem();
        sink.monospaced();
        text(className);
        sink.monospaced_();
        sink.listItem_();
      }
      sink.list_();
    }
    endSection();
  }

  private static String detailsAnchor(Entry entry) {
    return "dep-" + entry.coordinate().replaceAll("[^A-Za-z0-9._-]", "_");
  }

  private static String formatSize(long bytes) {
    return FileUtils.byteCountToDisplaySize(bytes);
  }

  private static String joinOrNone(Iterable<String> values) {
    List<String> list = new ArrayList<>();
    values.forEach(list::add);
    return list.isEmpty() ? "none" : String.join(", ", list);
  }

  /** One of the dependency groups DepClean reports on. */
  private static final class Category {
    final String title;
    final String anchor;
    final List<Entry> entries;

    Category(String title, String anchor, List<Entry> entries) {
      this.title = title;
      this.anchor = anchor;
      this.entries = Collections.unmodifiableList(entries);
    }
  }
}
