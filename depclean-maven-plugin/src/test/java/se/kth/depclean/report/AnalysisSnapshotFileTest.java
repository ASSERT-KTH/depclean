package se.kth.depclean.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link AnalysisSnapshotFile}. */
class AnalysisSnapshotFileTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

  @TempDir Path projectDir;

  private Path pom;
  private Path classes;
  private Path testClasses;
  private AnalysisSnapshotFile snapshotFile;
  private AnalysisSnapshot.Settings settings;

  @BeforeEach
  void setUp() throws IOException {
    pom = Files.write(projectDir.resolve("pom.xml"), "<project/>".getBytes("UTF-8"));
    Path target = projectDir.resolve("target");
    classes = Files.createDirectories(target.resolve("classes"));
    testClasses = Files.createDirectories(target.resolve("test-classes"));
    Files.createFile(classes.resolve("Foo.class"));
    Files.createFile(testClasses.resolve("FooTest.class"));
    snapshotFile = new AnalysisSnapshotFile(target);
    settings =
        new AnalysisSnapshot.Settings(
            true, Collections.singleton("provided"), Arrays.asList("com.google.guava.*"));
    // Everything the snapshot depends on predates the snapshot written by the tests
    for (Path path : Arrays.asList(pom, classes.resolve("Foo.class"), testClasses.resolve("FooTest.class"))) {
      Files.setLastModifiedTime(path, FileTime.from(T0));
    }
  }

  @Test
  void roundTripPreservesEntriesAndSettings() throws IOException {
    AnalysisSnapshot original = snapshot(settings);

    snapshotFile.write(original);
    Optional<AnalysisSnapshot> read = readFresh(settings);

    assertThat(read).isPresent();
    AnalysisSnapshot snapshot = read.get();
    assertThat(snapshot.getSettings()).isEqualTo(settings);
    assertThat(snapshot.getUsedDirect()).hasSize(1);
    AnalysisSnapshot.Entry used = snapshot.getUsedDirect().get(0);
    assertThat(used.coordinate()).isEqualTo("commons-io:commons-io:2.22.0");
    assertThat(used.getScope()).isEqualTo("compile");
    assertThat(used.getSizeBytes()).isEqualTo(608_000L);
    assertThat(used.getTotalClasses()).isEqualTo(300);
    assertThat(used.getUsedClasses())
        .containsExactly("org.apache.commons.io.FileUtils", "org.apache.commons.io.IOUtils");
    assertThat(snapshot.getUnusedTransitive()).extracting(AnalysisSnapshot.Entry::getScope)
        .containsExactly((String) null);
    assertThat(snapshot.getIgnored()).isEmpty();
  }

  @Test
  void isNotReusableWhenMissing() throws IOException {
    assertThat(readFresh(settings)).isEmpty();
  }

  @Test
  void isNotReusableWhenSettingsDiffer() throws IOException {
    snapshotFile.write(snapshot(settings));

    AnalysisSnapshot.Settings other =
        new AnalysisSnapshot.Settings(false, Collections.emptySet(), Collections.emptySet());
    assertThat(readFresh(other)).isEmpty();
  }

  @Test
  void isNotReusableWhenPomIsNewer() throws IOException {
    snapshotFile.write(snapshot(settings));
    touchAfterSnapshot(pom);

    assertThat(readFresh(settings)).isEmpty();
  }

  @Test
  void isNotReusableWhenAClassIsNewer() throws IOException {
    snapshotFile.write(snapshot(settings));
    touchAfterSnapshot(testClasses.resolve("FooTest.class"));

    assertThat(readFresh(settings)).isEmpty();
  }

  @Test
  void isNotReusableWhenCorrupt() throws IOException {
    Files.write(snapshotFile.getPath(), "{ not json".getBytes("UTF-8"));

    assertThat(readFresh(settings)).isEmpty();
  }

  @Test
  void ignoresMissingClassDirectories() throws IOException {
    snapshotFile.write(snapshot(settings));

    Optional<AnalysisSnapshot> read =
        snapshotFile.readIfFresh(
            settings, pom, Collections.singletonList(projectDir.resolve("does-not-exist")));

    assertThat(read).isPresent();
  }

  private Optional<AnalysisSnapshot> readFresh(AnalysisSnapshot.Settings with) throws IOException {
    return snapshotFile.readIfFresh(with, pom, Arrays.asList(classes, testClasses));
  }

  private void touchAfterSnapshot(Path path) throws IOException {
    FileTime snapshotTime = Files.getLastModifiedTime(snapshotFile.getPath());
    Files.setLastModifiedTime(path, FileTime.from(snapshotTime.toInstant().plusSeconds(5)));
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
            "commons-codec", "commons-codec", "1.19.0", null, 373_000L, 120, Collections.emptyList());
    return new AnalysisSnapshot(
        settings,
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
