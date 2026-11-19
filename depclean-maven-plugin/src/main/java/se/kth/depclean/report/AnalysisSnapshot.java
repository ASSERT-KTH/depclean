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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import se.kth.depclean.core.analysis.DependencyTypes;
import se.kth.depclean.core.analysis.model.AggregatorUsage;
import se.kth.depclean.core.analysis.model.DependencyUsage;
import se.kth.depclean.core.analysis.model.ProjectDependencyAnalysis;
import se.kth.depclean.core.model.ClassName;
import se.kth.depclean.core.model.Dependency;

/**
 * Serializable snapshot of a {@link ProjectDependencyAnalysis}: what {@code depclean:depclean}
 * hands over to {@code depclean:report} through {@code target/depclean-analysis.json}, so that the
 * report does not have to run the analysis a second time.
 */
@SuppressWarnings("java:S6206") // records are Java 16+, the plugin is built with --release 8
public final class AnalysisSnapshot {

  /**
   * The version of the file format. A snapshot written with another version, e.g. by an older
   * plugin, misses information and is not reused.
   */
  public static final int FORMAT_VERSION = 2;

  // Assigned in the constructor, not inline, so that it is read from the file and not inlined
  private final int formatVersion;
  private final Settings settings;
  private final String inputsFingerprint;
  private final List<Entry> usedDirect;
  private final List<Entry> usedTransitive;
  private final List<Entry> usedInheritedDirect;
  private final List<Entry> usedInheritedTransitive;
  private final List<Entry> unusedDirect;
  private final List<Entry> unusedTransitive;
  private final List<Entry> unusedInheritedDirect;
  private final List<Entry> unusedInheritedTransitive;
  private final List<Entry> ignored;
  private final List<Entry> usedOnlyByTests;

  /**
   * Creates a snapshot from already categorized entries.
   *
   * @param inputsFingerprint the {@link AnalysisInputs#fingerprint} of what was analysed
   */
  @SuppressWarnings("java:S107") // the parameters mirror the analysis categories one-to-one
  public AnalysisSnapshot(
      Settings settings,
      String inputsFingerprint,
      List<Entry> usedDirect,
      List<Entry> usedTransitive,
      List<Entry> usedInheritedDirect,
      List<Entry> usedInheritedTransitive,
      List<Entry> unusedDirect,
      List<Entry> unusedTransitive,
      List<Entry> unusedInheritedDirect,
      List<Entry> unusedInheritedTransitive,
      List<Entry> ignored) {
    this(
        settings,
        inputsFingerprint,
        usedDirect,
        usedTransitive,
        usedInheritedDirect,
        usedInheritedTransitive,
        unusedDirect,
        unusedTransitive,
        unusedInheritedDirect,
        unusedInheritedTransitive,
        ignored,
        Collections.emptyList());
  }

  /**
   * Creates a snapshot from already categorized entries.
   *
   * @param inputsFingerprint the {@link AnalysisInputs#fingerprint} of what was analysed
   * @param usedOnlyByTests the declared dependencies with a packaged scope that only the tests use;
   *     they are also in one of the used categories
   */
  @SuppressWarnings("java:S107") // the parameters mirror the analysis categories one-to-one
  public AnalysisSnapshot(
      Settings settings,
      String inputsFingerprint,
      List<Entry> usedDirect,
      List<Entry> usedTransitive,
      List<Entry> usedInheritedDirect,
      List<Entry> usedInheritedTransitive,
      List<Entry> unusedDirect,
      List<Entry> unusedTransitive,
      List<Entry> unusedInheritedDirect,
      List<Entry> unusedInheritedTransitive,
      List<Entry> ignored,
      List<Entry> usedOnlyByTests) {
    this.formatVersion = FORMAT_VERSION;
    this.settings = settings;
    this.inputsFingerprint = inputsFingerprint;
    this.usedDirect = sorted(usedDirect);
    this.usedTransitive = sorted(usedTransitive);
    this.usedInheritedDirect = sorted(usedInheritedDirect);
    this.usedInheritedTransitive = sorted(usedInheritedTransitive);
    this.unusedDirect = sorted(unusedDirect);
    this.unusedTransitive = sorted(unusedTransitive);
    this.unusedInheritedDirect = sorted(unusedInheritedDirect);
    this.unusedInheritedTransitive = sorted(unusedInheritedTransitive);
    this.ignored = sorted(ignored);
    this.usedOnlyByTests = sorted(usedOnlyByTests);
  }

  /**
   * Captures the result of an analysis together with the settings and the fingerprint of the inputs
   * that produced it.
   */
  public static AnalysisSnapshot from(
      ProjectDependencyAnalysis analysis, Settings settings, String inputsFingerprint) {
    return new AnalysisSnapshot(
        settings,
        inputsFingerprint,
        entries(analysis, analysis.getUsedDirectDependencies()),
        entries(analysis, analysis.getUsedTransitiveDependencies()),
        entries(analysis, analysis.getUsedInheritedDirectDependencies()),
        entries(analysis, analysis.getUsedInheritedTransitiveDependencies()),
        entries(analysis, analysis.getUnusedDirectDependencies()),
        entries(analysis, analysis.getUnusedTransitiveDependencies()),
        entries(analysis, analysis.getUnusedInheritedDirectDependencies()),
        entries(analysis, analysis.getUnusedInheritedTransitiveDependencies()),
        entries(analysis, analysis.getIgnoredDependencies()),
        entries(analysis, analysis.getDependenciesUsedOnlyByTests()));
  }

  @SuppressWarnings("java:S6204") // Stream.toList() is Java 16+, the plugin targets Java 8
  private static List<Entry> entries(
      ProjectDependencyAnalysis analysis, Collection<Dependency> dependencies) {
    return dependencies.stream()
        .map(dependency ->
            Entry.from(
                dependency,
                analysis.getDependencyClassesMap().get(dependency),
                analysis.getDependencyUsages().get(dependency),
                analysis.getAggregators().get(dependency)))
        .collect(Collectors.toList());
  }

  private static List<Entry> sorted(List<Entry> entries) {
    List<Entry> copy = new ArrayList<>(entries);
    copy.sort(Comparator.comparing(Entry::coordinate));
    return Collections.unmodifiableList(copy);
  }

  /**
   * The version of the format the snapshot was written with.
   *
   * @return {@link #FORMAT_VERSION} for a snapshot created by this plugin, 0 for one read from a
   *     file written before the format had a version
   */
  public int getFormatVersion() {
    return formatVersion;
  }

  public Settings getSettings() {
    return settings;
  }

  public String getInputsFingerprint() {
    return inputsFingerprint;
  }

  public List<Entry> getUsedDirect() {
    return usedDirect;
  }

  public List<Entry> getUsedTransitive() {
    return usedTransitive;
  }

  public List<Entry> getUsedInheritedDirect() {
    return usedInheritedDirect;
  }

  public List<Entry> getUsedInheritedTransitive() {
    return usedInheritedTransitive;
  }

  public List<Entry> getUnusedDirect() {
    return unusedDirect;
  }

  public List<Entry> getUnusedTransitive() {
    return unusedTransitive;
  }

  public List<Entry> getUnusedInheritedDirect() {
    return unusedInheritedDirect;
  }

  public List<Entry> getUnusedInheritedTransitive() {
    return unusedInheritedTransitive;
  }

  public List<Entry> getIgnored() {
    return ignored;
  }

  /**
   * The declared dependencies with a packaged scope (compile, runtime) that only the tests use:
   * they can be declared with the test scope.
   */
  public List<Entry> getUsedOnlyByTests() {
    return usedOnlyByTests;
  }

  /** The analysis settings that influence the result; a snapshot is only reusable if they match. */
  @SuppressWarnings("java:S6206") // records are Java 16+, the plugin is built with --release 8
  public static final class Settings {

    private final boolean ignoreTests;
    private final Set<String> ignoreScopes;
    private final Set<String> ignoreDependencies;

    /** Creates the settings; both sets are copied into sorted sets. */
    public Settings(
        boolean ignoreTests,
        Collection<String> ignoreScopes,
        Collection<String> ignoreDependencies) {
      this.ignoreTests = ignoreTests;
      this.ignoreScopes = Collections.unmodifiableSet(new TreeSet<>(ignoreScopes));
      this.ignoreDependencies = Collections.unmodifiableSet(new TreeSet<>(ignoreDependencies));
    }

    public boolean isIgnoreTests() {
      return ignoreTests;
    }

    public Set<String> getIgnoreScopes() {
      return ignoreScopes;
    }

    public Set<String> getIgnoreDependencies() {
      return ignoreDependencies;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Settings)) {
        return false;
      }
      Settings that = (Settings) o;
      return ignoreTests == that.ignoreTests
          && ignoreScopes.equals(that.ignoreScopes)
          && ignoreDependencies.equals(that.ignoreDependencies);
    }

    @Override
    public int hashCode() {
      return Objects.hash(ignoreTests, ignoreScopes, ignoreDependencies);
    }
  }

  /** One dependency of the analysed project. */
  @SuppressWarnings("java:S6206") // records are Java 16+, the plugin is built with --release 8
  public static final class Entry {

    private final String groupId;
    private final String artifactId;
    private final String version;
    @Nullable private final String scope;
    private final long sizeBytes;
    private final int totalClasses;
    private final List<String> usedClasses;
    @Nullable private final Usage usage;
    @Nullable private final Aggregator aggregator;

    /** Creates an entry for a dependency that is neither explained nor an aggregator. */
    public Entry(
        String groupId,
        String artifactId,
        String version,
        @Nullable String scope,
        long sizeBytes,
        int totalClasses,
        Collection<String> usedClasses) {
      this(groupId, artifactId, version, scope, sizeBytes, totalClasses, usedClasses, null, null);
    }

    /**
     * Creates an entry.
     *
     * @param usage why the dependency is used, null if it is not
     * @param aggregator how much of what the dependency brings is used, null if it is not an
     *     aggregator
     */
    @SuppressWarnings("java:S107") // the parameters mirror the reported facts one-to-one
    public Entry(
        String groupId,
        String artifactId,
        String version,
        @Nullable String scope,
        long sizeBytes,
        int totalClasses,
        Collection<String> usedClasses,
        @Nullable Usage usage,
        @Nullable Aggregator aggregator) {
      this.usage = usage;
      this.aggregator = aggregator;
      this.groupId = groupId;
      this.artifactId = artifactId;
      this.version = version;
      this.scope = scope;
      this.sizeBytes = sizeBytes;
      this.totalClasses = totalClasses;
      this.usedClasses = Collections.unmodifiableList(new ArrayList<>(new TreeSet<>(usedClasses)));
    }

    @SuppressWarnings("java:S6204") // Stream.toList() is Java 16+, the plugin targets Java 8
    static Entry from(
        Dependency dependency,
        @Nullable DependencyTypes types,
        @Nullable DependencyUsage usage,
        @Nullable AggregatorUsage aggregator) {
      int totalClasses = 0;
      for (ClassName ignored : dependency.getRelatedClasses()) {
        totalClasses++;
      }
      List<String> usedClasses =
          types == null
              ? Collections.emptyList()
              : types.getUsedTypes().stream().map(ClassName::getValue).collect(Collectors.toList());
      return new Entry(
          dependency.getGroupId(),
          dependency.getDependencyId(),
          dependency.getVersion(),
          dependency.getScope(),
          dependency.getSize(),
          totalClasses,
          usedClasses,
          usage == null ? null : Usage.from(usage),
          aggregator == null
              ? null
              : new Aggregator(aggregator.getDependencies(), aggregator.getUsedDependencies()));
    }

    public String getGroupId() {
      return groupId;
    }

    public String getArtifactId() {
      return artifactId;
    }

    public String getVersion() {
      return version;
    }

    @Nullable
    public String getScope() {
      return scope;
    }

    public long getSizeBytes() {
      return sizeBytes;
    }

    public int getTotalClasses() {
      return totalClasses;
    }

    public List<String> getUsedClasses() {
      return usedClasses;
    }

    /** Why the dependency is used, null if it is not. */
    @Nullable
    public Usage getUsage() {
      return usage;
    }

    /** How much of what the dependency brings is used, null if it is not an aggregator. */
    @Nullable
    public Aggregator getAggregator() {
      return aggregator;
    }

    /** The {@code groupId:artifactId:version} coordinate. */
    public String coordinate() {
      return groupId + ":" + artifactId + ":" + version;
    }
  }

  /** Why a dependency is used; see {@link DependencyUsage} for the meaning of each list. */
  @SuppressWarnings("java:S6206") // records are Java 16+, the plugin is built with --release 8
  public static final class Usage {

    private final List<String> mainClasses;
    private final List<String> testClasses;
    private final List<String> dependencies;
    private final List<String> services;
    private final List<String> xmlResources;
    private final List<String> imports;
    private final List<String> annotationProcessors;
    private final boolean ignored;

    /** Creates the usage of a dependency; the lists are copied and sorted. */
    @SuppressWarnings("java:S107") // the parameters mirror the kinds of evidence one-to-one
    public Usage(
        Collection<String> mainClasses,
        Collection<String> testClasses,
        Collection<String> dependencies,
        Collection<String> services,
        Collection<String> xmlResources,
        Collection<String> imports,
        Collection<String> annotationProcessors,
        boolean ignored) {
      this.mainClasses = sortedCopy(mainClasses);
      this.testClasses = sortedCopy(testClasses);
      this.dependencies = sortedCopy(dependencies);
      this.services = sortedCopy(services);
      this.xmlResources = sortedCopy(xmlResources);
      this.imports = sortedCopy(imports);
      this.annotationProcessors = sortedCopy(annotationProcessors);
      this.ignored = ignored;
    }

    static Usage from(DependencyUsage usage) {
      return new Usage(
          usage.getMainClasses(),
          usage.getTestClasses(),
          usage.getDependencies(),
          usage.getServices(),
          usage.getXmlResourceClasses(),
          usage.getImportedClasses(),
          usage.getAnnotationProcessors(),
          usage.isIgnored());
    }

    private static List<String> sortedCopy(Collection<String> values) {
      return Collections.unmodifiableList(new ArrayList<>(new TreeSet<>(values)));
    }

    public List<String> getMainClasses() {
      return mainClasses;
    }

    public List<String> getTestClasses() {
      return testClasses;
    }

    public List<String> getDependencies() {
      return dependencies;
    }

    public List<String> getServices() {
      return services;
    }

    public List<String> getXmlResources() {
      return xmlResources;
    }

    public List<String> getImports() {
      return imports;
    }

    public List<String> getAnnotationProcessors() {
      return annotationProcessors;
    }

    public boolean isIgnored() {
      return ignored;
    }
  }

  /** How much of what an aggregator brings is used; see {@link AggregatorUsage}. */
  @SuppressWarnings("java:S6206") // records are Java 16+, the plugin is built with --release 8
  public static final class Aggregator {

    private final int dependencies;
    private final int usedDependencies;

    /** Creates the usage of an aggregator. */
    public Aggregator(int dependencies, int usedDependencies) {
      this.dependencies = dependencies;
      this.usedDependencies = usedDependencies;
    }

    /** The dependencies with classes that the aggregator brings. */
    public int getDependencies() {
      return dependencies;
    }

    /** How many of the dependencies the aggregator brings are used. */
    public int getUsedDependencies() {
      return usedDependencies;
    }
  }
}
