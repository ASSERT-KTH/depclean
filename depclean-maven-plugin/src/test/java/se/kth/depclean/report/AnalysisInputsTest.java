package se.kth.depclean.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link AnalysisInputs}. */
class AnalysisInputsTest {

  @TempDir Path projectDir;

  private Path pom;
  private Path classes;
  private Path testClasses;
  private List<Path> classDirectories;

  @BeforeEach
  void setUp() throws IOException {
    pom = write(projectDir.resolve("pom.xml"), "<project/>");
    classes = Files.createDirectories(projectDir.resolve("target/classes"));
    testClasses = Files.createDirectories(projectDir.resolve("target/test-classes"));
    write(classes.resolve("Foo.class"), "foo");
    write(classes.resolve("sub/Bar.class"), "bar");
    write(testClasses.resolve("FooTest.class"), "foo-test");
    classDirectories = Arrays.asList(classes, testClasses);
  }

  @Test
  void isStableAcrossRuns() throws IOException {
    assertThat(fingerprint()).isEqualTo(fingerprint()).hasSize(64).matches("[0-9a-f]+");
  }

  @Test
  void ignoresTimestampsOfUnchangedFiles() throws IOException {
    String before = fingerprint();
    // What a forked lifecycle does when it recompiles identical sources
    Files.setLastModifiedTime(
        classes.resolve("Foo.class"), FileTime.from(Instant.parse("2030-01-01T00:00:00Z")));

    assertThat(fingerprint()).isEqualTo(before);
  }

  @Test
  void changesWhenPomChanges() throws IOException {
    String before = fingerprint();
    write(pom, "<project><name>changed</name></project>");

    assertThat(fingerprint()).isNotEqualTo(before);
  }

  @Test
  void changesWhenAClassChanges() throws IOException {
    String before = fingerprint();
    write(testClasses.resolve("FooTest.class"), "changed");

    assertThat(fingerprint()).isNotEqualTo(before);
  }

  @Test
  void changesWhenAClassIsAddedOrMoved() throws IOException {
    String before = fingerprint();
    write(classes.resolve("Baz.class"), "baz");
    String added = fingerprint();
    Files.move(classes.resolve("Baz.class"), classes.resolve("sub/Baz.class"));

    assertThat(added).isNotEqualTo(before);
    assertThat(fingerprint()).isNotEqualTo(before).isNotEqualTo(added);
  }

  @Test
  void ignoresNonClassFilesAndMissingDirectories() throws IOException {
    String before = fingerprint();
    write(classes.resolve("application.properties"), "key=value");

    assertThat(fingerprint()).isEqualTo(before);
    assertThat(
            AnalysisInputs.fingerprint(
                pom, Arrays.asList(classes, testClasses, projectDir.resolve("does-not-exist"))))
        .isEqualTo(before);
    assertThat(AnalysisInputs.fingerprint(pom, Collections.emptyList())).isNotEqualTo(before);
  }

  private String fingerprint() throws IOException {
    return AnalysisInputs.fingerprint(pom, classDirectories);
  }

  private static Path write(Path file, String content) throws IOException {
    Files.createDirectories(file.getParent());
    return Files.write(file, content.getBytes(StandardCharsets.UTF_8));
  }
}
