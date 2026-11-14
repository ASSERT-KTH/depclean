package se.kth.depclean.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClassFilesTest {

  @TempDir Path temp;

  @Test
  void convertsPathsToClassNames() {
    assertThat(ClassFiles.toClassName("com/foo/Bar.class")).isEqualTo("com.foo.Bar");
    assertThat(ClassFiles.toClassName("com/foo/Bar$Inner.class")).isEqualTo("com.foo.Bar$Inner");
    assertThat(ClassFiles.toClassName("Default.class")).isEqualTo("Default");
    assertThat(ClassFiles.toClassName("META-INF/versions/11/com/foo/Bar.class"))
        .isEqualTo("com.foo.Bar");
  }

  @Test
  void ignoresFilesThatAreNotTheClassFileOfAType() {
    assertThat(ClassFiles.toClassName("module-info.class")).isNull();
    assertThat(ClassFiles.toClassName("com/foo/package-info.class")).isNull();
    assertThat(ClassFiles.toClassName("META-INF/versions/9/module-info.class")).isNull();
    assertThat(ClassFiles.toClassName("com/foo/Bar.java")).isNull();
    assertThat(ClassFiles.toClassName("META-INF/MANIFEST.MF")).isNull();
  }

  @Test
  void listsTheClassesOfAJarOnceAndPrefersTheBaseVersion() throws Exception {
    Path jar = temp.resolve("lib.jar");
    try (OutputStream out = Files.newOutputStream(jar);
        JarOutputStream jarOut = new JarOutputStream(out)) {
      for (String entry :
          new String[] {
            "META-INF/versions/11/com/foo/Bar.class",
            "com/foo/Bar.class",
            "com/foo/package-info.class",
            "META-INF/versions/17/com/foo/OnlyNew.class",
            "module-info.class",
            "readme.txt"
          }) {
        jarOut.putNextEntry(new JarEntry(entry));
        jarOut.closeEntry();
      }
    }

    Map<String, String> classes = ClassFiles.list(jar.toFile());

    assertThat(classes)
        .containsOnlyKeys("com.foo.Bar", "com.foo.OnlyNew")
        .containsEntry("com.foo.OnlyNew", "META-INF/versions/17/com/foo/OnlyNew.class");
  }

  @Test
  void listsTheClassesOfADirectory() throws Exception {
    Files.createDirectories(temp.resolve("classes/com/foo"));
    Files.createFile(temp.resolve("classes/com/foo/Bar.class"));
    Files.createFile(temp.resolve("classes/Root.class"));
    Files.createFile(temp.resolve("classes/com/foo/notes.txt"));

    assertThat(ClassFiles.list(temp.resolve("classes").toFile()))
        .containsOnlyKeys("com.foo.Bar", "Root")
        .containsEntry("com.foo.Bar", "com/foo/Bar.class");
  }

  @Test
  void survivesADirectoryItCannotRead() throws Exception {
    Files.createDirectories(temp.resolve("classes/com/foo"));
    Files.createFile(temp.resolve("classes/com/foo/Bar.class"));
    Path locked = Files.createDirectories(temp.resolve("classes/locked"));
    Files.createFile(locked.resolve("Hidden.class"));
    assumeTrue(locked.toFile().setReadable(false, false) && !Files.isReadable(locked));
    try {
      // the walk stops at the unreadable directory, but no exception escapes
      assertThat(ClassFiles.list(temp.resolve("classes").toFile()))
          .doesNotContainKey("locked.Hidden");
    } finally {
      locked.toFile().setReadable(true, false);
    }
  }

  @Test
  void hasNoClassesForAnythingElse() throws Exception {
    Path pom = Files.createFile(temp.resolve("artifact.pom"));

    assertThat(ClassFiles.list(pom.toFile())).isEmpty();
    assertThat(ClassFiles.list(temp.resolve("missing.jar").toFile())).isEmpty();
  }
}
