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
import se.kth.depclean.core.analysis.model.ProjectDependencyAnalysis;
import se.kth.depclean.core.model.ClassName;
import se.kth.depclean.core.model.Dependency;

/**
 * Serializable snapshot of a {@link ProjectDependencyAnalysis}: what {@code depclean:depclean}
 * hands over to {@code depclean:report} through {@code target/depclean-analysis.json}, so that the
 * report does not have to run the analysis a second time.
 */
public final class AnalysisSnapshot {

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

  /**
   * Creates a snapshot from already categorized entries.
   *
   * @param inputsFingerprint the {@link AnalysisInputs#fingerprint} of what was analysed
   */
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
        entries(analysis, analysis.getIgnoredDependencies()));
  }

  private static List<Entry> entries(
      ProjectDependencyAnalysis analysis, Collection<Dependency> dependencies) {
    return dependencies.stream()
        .map(
            dependency ->
                Entry.from(dependency, analysis.getDependencyClassesMap().get(dependency)))
        .collect(Collectors.toList());
  }

  private static List<Entry> sorted(List<Entry> entries) {
    List<Entry> copy = new ArrayList<>(entries);
    copy.sort(Comparator.comparing(Entry::coordinate));
    return Collections.unmodifiableList(copy);
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

  /** The analysis settings that influence the result; a snapshot is only reusable if they match. */
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
  public static final class Entry {

    private final String groupId;
    private final String artifactId;
    private final String version;
    @Nullable private final String scope;
    private final long sizeBytes;
    private final int totalClasses;
    private final List<String> usedClasses;

    /** Creates an entry. */
    public Entry(
        String groupId,
        String artifactId,
        String version,
        @Nullable String scope,
        long sizeBytes,
        int totalClasses,
        Collection<String> usedClasses) {
      this.groupId = groupId;
      this.artifactId = artifactId;
      this.version = version;
      this.scope = scope;
      this.sizeBytes = sizeBytes;
      this.totalClasses = totalClasses;
      this.usedClasses = Collections.unmodifiableList(new ArrayList<>(new TreeSet<>(usedClasses)));
    }

    static Entry from(Dependency dependency, @Nullable DependencyTypes types) {
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
          usedClasses);
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

    /** The {@code groupId:artifactId:version} coordinate. */
    public String coordinate() {
      return groupId + ":" + artifactId + ":" + version;
    }
  }
}
