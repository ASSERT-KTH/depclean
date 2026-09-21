package se.kth.depclean.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link AnalysisSnapshotFile}. */
class AnalysisSnapshotFileTest {

  private static final String FINGERPRINT = "0123abcd";

  @TempDir Path target;

  private AnalysisSnapshotFile snapshotFile;
  private AnalysisSnapshot.Settings settings;

  @BeforeEach
  void setUp() {
    snapshotFile = new AnalysisSnapshotFile(target);
    settings =
        new AnalysisSnapshot.Settings(
            true, Collections.singleton("provided"), Arrays.asList("com.google.guava.*"));
  }

  @Test
  void roundTripPreservesEntriesSettingsAndFingerprint() throws IOException {
    AnalysisSnapshot original = snapshot(settings);

    snapshotFile.write(original);
    Optional<AnalysisSnapshot> read = snapshotFile.readIfFresh(settings, FINGERPRINT);

    assertThat(read).isPresent();
    AnalysisSnapshot snapshot = read.get();
    assertThat(snapshot.getSettings()).isEqualTo(settings);
    assertThat(snapshot.getInputsFingerprint()).isEqualTo(FINGERPRINT);
    assertThat(snapshot.getUsedDirect()).hasSize(1);
    AnalysisSnapshot.Entry used = snapshot.getUsedDirect().get(0);
    assertThat(used.coordinate()).isEqualTo("commons-io:commons-io:2.22.0");
    assertThat(used.getScope()).isEqualTo("compile");
    assertThat(used.getSizeBytes()).isEqualTo(608_000L);
    assertThat(used.getTotalClasses()).isEqualTo(300);
    assertThat(used.getUsedClasses())
        .containsExactly("org.apache.commons.io.FileUtils", "org.apache.commons.io.IOUtils");
    assertThat(snapshot.getUnusedTransitive())
        .extracting(AnalysisSnapshot.Entry::getScope)
        .containsExactly((String) null);
    assertThat(snapshot.getIgnored()).isEmpty();
  }

  @Test
  void isNotReusableWhenMissing() throws IOException {
    assertThat(snapshotFile.readIfFresh(settings, FINGERPRINT)).isEmpty();
  }

  @Test
  void isNotReusableWhenSettingsDiffer() throws IOException {
    snapshotFile.write(snapshot(settings));

    AnalysisSnapshot.Settings other =
        new AnalysisSnapshot.Settings(false, Collections.emptySet(), Collections.emptySet());
    assertThat(snapshotFile.readIfFresh(other, FINGERPRINT)).isEmpty();
  }

  @Test
  void isNotReusableWhenInputsChanged() throws IOException {
    snapshotFile.write(snapshot(settings));

    assertThat(snapshotFile.readIfFresh(settings, "different")).isEmpty();
  }

  @Test
  void isNotReusableWhenCorrupt() throws IOException {
    Files.write(snapshotFile.getPath(), "{ not json".getBytes("UTF-8"));

    assertThat(snapshotFile.readIfFresh(settings, FINGERPRINT)).isEmpty();
  }

  @Test
  void isNotReusableWhenWrittenByAnOlderVersionWithoutFingerprint() throws IOException {
    Files.write(
        snapshotFile.getPath(),
        "{\"settings\":{\"ignoreTests\":false,\"ignoreScopes\":[],\"ignoreDependencies\":[]}}"
            .getBytes("UTF-8"));

    AnalysisSnapshot.Settings plain =
        new AnalysisSnapshot.Settings(false, Collections.emptySet(), Collections.emptySet());
    assertThat(snapshotFile.readIfFresh(plain, FINGERPRINT)).isEmpty();
  }

  static AnalysisSnapshot snapshot(AnalysisSnapshot.Settings settings) {
    AnalysisSnapshot.Entry used =
        new AnalysisSnapshot.Entry(
            "commons-io",
            "commons-io",
            "2.22.0",
            "compile",
            608_000L,
            300,
            Arrays.asList("org.apache.commons.io.IOUtils", "org.apache.commons.io.FileUtils"));
    AnalysisSnapshot.Entry unused =
        new AnalysisSnapshot.Entry(
            "commons-codec",
            "commons-codec",
            "1.19.0",
            null,
            373_000L,
            120,
            Collections.emptyList());
    return new AnalysisSnapshot(
        settings,
        FINGERPRINT,
        Collections.singletonList(used),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.singletonList(unused),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList());
  }
}
