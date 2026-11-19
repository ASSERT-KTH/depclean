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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.reporting.AbstractMavenReportRenderer;
import se.kth.depclean.report.AnalysisSnapshot.Entry;

/**
 * Renders an {@link AnalysisSnapshot} as a Doxia document, i.e. the body of the DepClean site page.
 */
public final class DepCleanReportRenderer extends AbstractMavenReportRenderer {

  private static final String[] DEPENDENCY_TABLE_HEADER = {
    "Group ID", "Artifact ID", "Version", "Scope", "Size", "Used classes", "Notes"
  };

  private static final String[] TEST_ONLY_TABLE_HEADER = {
    "Group ID", "Artifact ID", "Version", "Scope", "Size"
  };

  private static final String[] AGGREGATOR_TABLE_HEADER = {
    "Group ID", "Artifact ID", "Version", "Scope", "Category", "Used dependencies it brings"
  };

  /** How many class names of one kind of evidence the details of a dependency list. */
  private static final int MAX_LISTED_CLASSES = 20;

  private final AnalysisSnapshot snapshot;
  private final String projectName;
  private final boolean reused;
  private final List<Category> categories;
  private final Set<String> usedOnlyByTests = new HashSet<>();
  private final Map<String, Category> aggregators = new LinkedHashMap<>();

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
    snapshot.getUsedOnlyByTests().forEach(entry -> usedOnlyByTests.add(entry.coordinate()));
    // the declared aggregators, with the category they are in
    for (Category category : categories) {
      if (category.anchor.endsWith("-direct")) {
        for (Entry entry : category.entries) {
          if (entry.getAggregator() != null) {
            aggregators.putIfAbsent(entry.coordinate(), category);
          }
        }
      }
    }
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
    renderUsedOnlyByTests();
    renderAggregators();
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
    tableRow(
        new String[] {
          createLinkPatternedText("Used only by the tests", "#test-only"),
          String.valueOf(snapshot.getUsedOnlyByTests().size()),
          formatSize(snapshot.getUsedOnlyByTests().stream().mapToLong(Entry::getSizeBytes).sum())
        });
    tableRow(
        new String[] {
          createLinkPatternedText("Aggregators", "#aggregators"),
          String.valueOf(aggregators.size()),
          "-"
        });
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
              usedClassesCell(entry),
              notes(entry)
            });
      }
      endTable();
    }
    endSection();
  }

  /** A short summary of what the analysis knows about a dependency besides its category. */
  private String notes(Entry entry) {
    List<String> notes = new ArrayList<>();
    AnalysisSnapshot.Usage usage = entry.getUsage();
    if (usage != null) {
      notes.addAll(usageSummary(usage));
    }
    if (usedOnlyByTests.contains(entry.coordinate())) {
      notes.add("only the tests use it");
    }
    AnalysisSnapshot.Aggregator aggregator = entry.getAggregator();
    if (aggregator != null) {
      notes.add("aggregator: " + aggregatorUsage(aggregator));
    }
    return String.join("; ", notes);
  }

  private static List<String> usageSummary(AnalysisSnapshot.Usage usage) {
    List<String> summary = new ArrayList<>();
    if (!usage.getMainClasses().isEmpty()) {
      summary.add(
          "referenced by " + count(usage.getMainClasses().size(), "main class", "main classes"));
    }
    if (!usage.getTestClasses().isEmpty()) {
      summary.add(
          "referenced by " + count(usage.getTestClasses().size(), "test class", "test classes"));
    }
    if (!usage.getDependencies().isEmpty()) {
      summary.add(
          "referenced by " + count(usage.getDependencies().size(), "dependency", "dependencies"));
    }
    if (!usage.getServices().isEmpty()) {
      summary.add("provides " + count(usage.getServices().size(), "service", "services"));
    }
    if (!usage.getXmlResources().isEmpty()) {
      summary.add("named in XML resources");
    }
    if (!usage.getImports().isEmpty()) {
      summary.add("imported in the sources");
    }
    if (!usage.getAnnotationProcessors().isEmpty()) {
      summary.add("annotation processor");
    }
    if (usage.isIgnored()) {
      summary.add("ignored by the configuration");
    }
    return summary;
  }

  private static String count(int count, String singular, String plural) {
    return count + " " + (count == 1 ? singular : plural);
  }

  private static String aggregatorUsage(AnalysisSnapshot.Aggregator aggregator) {
    return aggregator.getUsedDependencies()
        + " of "
        + aggregator.getDependencies()
        + " dependencies it brings are used";
  }

  private void renderUsedOnlyByTests() {
    List<Entry> entries = snapshot.getUsedOnlyByTests();
    startSection("Used only by the tests (" + entries.size() + ")", "test-only");
    if (entries.isEmpty()) {
      paragraph("None.");
    } else {
      paragraph(
          "These dependencies are declared with a scope that ships them with the project (compile"
              + " or runtime), but only the tests use them. They can be declared with the test"
              + " scope.");
      startTable();
      tableHeader(TEST_ONLY_TABLE_HEADER);
      for (Entry entry : entries) {
        tableRow(
            new String[] {
              entry.getGroupId(),
              entry.getArtifactId(),
              entry.getVersion(),
              entry.getScope(),
              formatSize(entry.getSizeBytes())
            });
      }
      endTable();
    }
    endSection();
  }

  private void renderAggregators() {
    startSection("Aggregators (" + aggregators.size() + ")", "aggregators");
    if (aggregators.isEmpty()) {
      paragraph("None.");
    } else {
      paragraph(
          "These declared dependencies have no classes of their own: they only bring other"
              + " dependencies, like Spring Boot starters. Their own bytecode cannot be used, so"
              + " they keep their category; what matters is how much of what they bring is used."
              + " Removing one removes what it brings, unless it is declared otherwise.");
      startTable();
      tableHeader(AGGREGATOR_TABLE_HEADER);
      for (Category category : categories) {
        for (Entry entry : category.entries) {
          AnalysisSnapshot.Aggregator aggregator = entry.getAggregator();
          if (aggregator != null && aggregators.get(entry.coordinate()) == category) {
            tableRow(
                new String[] {
                  entry.getGroupId(),
                  entry.getArtifactId(),
                  entry.getVersion(),
                  entry.getScope(),
                  createLinkPatternedText(category.title, "#" + category.anchor),
                  aggregator.getUsedDependencies() + " / " + aggregator.getDependencies()
                });
          }
        }
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
      paragraph(
          "Why each used dependency is used, and the classes of it that the project reaches.");
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
    AnalysisSnapshot.Usage usage = entry.getUsage();
    if (usage != null) {
      renderUsage(usage);
    }
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

  private void renderUsage(AnalysisSnapshot.Usage usage) {
    sink.list();
    usageItem("Referenced by the main code", usage.getMainClasses());
    usageItem("Referenced by the tests", usage.getTestClasses());
    usageItem("Referenced by the dependencies", usage.getDependencies());
    usageItem("Provides an implementation of", usage.getServices());
    usageItem("Named in XML resources", usage.getXmlResources());
    usageItem("Imported in the sources", usage.getImports());
    usageItem("Annotation processor of the build", usage.getAnnotationProcessors());
    if (usage.isIgnored()) {
      sink.listItem();
      text("Ignored by the configuration (ignoreDependencies or ignoreScopes): considered used");
      sink.listItem_();
    }
    sink.list_();
  }

  /** One kind of evidence, with at most {@link #MAX_LISTED_CLASSES} of its names. */
  private void usageItem(String label, List<String> names) {
    if (names.isEmpty()) {
      return;
    }
    sink.listItem();
    text(label + ": ");
    List<String> listed = names.subList(0, Math.min(names.size(), MAX_LISTED_CLASSES));
    for (int i = 0; i < listed.size(); i++) {
      if (i > 0) {
        text(", ");
      }
      sink.monospaced();
      text(listed.get(i));
      sink.monospaced_();
    }
    if (names.size() > listed.size()) {
      text(" and " + (names.size() - listed.size()) + " more");
    }
    sink.listItem_();
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
