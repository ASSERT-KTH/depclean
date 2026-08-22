package se.kth.depclean.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link MavenInvoker}. */
class MavenInvokerTest {

  static final File expectedTree =
      new File(
          "src/test/resources/MavenInvokerResources/basic_spring_maven_project/tree_expected.txt");
  static final File producedTree =
      new File(
          "src/test/resources/MavenInvokerResources/basic_spring_maven_project/tree_produced.txt");
  static final File nullDirectoryProducedTree = new File("target/tree_null_directory.txt");

  @Test
  @DisplayName("Test that the Maven dependency tree, then the dependency tree is obtained")
  void testRunCommandToGetDependencyTree() throws IOException, InterruptedException {
    File directory =
        new File("src/test/resources/MavenInvokerResources/basic_spring_maven_project");
    MavenInvoker.runCommand(
        List.of("mvn", "dependency:tree", "-DoutputFile=tree_produced.txt"), directory);
    assertTrue(producedTree.exists());
    assertThat(producedTree).hasSameTextualContentAs(expectedTree);
  }

  @Test
  @DisplayName("Test that a command with a null working directory is executed")
  void testRunCommandWithNullDirectory() throws IOException, InterruptedException {
    if (nullDirectoryProducedTree.exists()) {
      FileUtils.forceDelete(nullDirectoryProducedTree);
    }
    MavenInvoker.runCommand(
        List.of(
            "mvn",
            "-f",
            "src/test/resources/MavenInvokerResources/basic_spring_maven_project/pom.xml",
            "dependency:tree",
            "-DoutputFile=" + nullDirectoryProducedTree.getAbsolutePath()),
        null);
    assertThat(nullDirectoryProducedTree).exists();
  }

  @Test
  @DisplayName("Test that a failing command raises an exception mentioning the exit code")
  void testRunCommandThrowsWhenTheCommandFails() {
    // An invalid JVM option makes `java` fail with a non-zero exit code on every platform, and
    // only requires the JDK that already runs this test.
    assertThatThrownBy(
            () -> MavenInvoker.runCommand(List.of(javaExecutable(), "-XXinvalidOption"), null))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("terminated with exit code")
        .hasMessageContaining("XXinvalidOption");
  }

  @Test
  @DisplayName("Test that an unknown executable raises an exception")
  void testRunCommandThrowsWhenTheExecutableDoesNotExist(@TempDir Path tempDir) {
    String missingExecutable = tempDir.resolve("no-such-executable").toAbsolutePath().toString();
    assertThatThrownBy(() -> MavenInvoker.runCommand(List.of(missingExecutable), null))
        .isInstanceOf(IOException.class);
  }

  @Test
  @DisplayName("Test that an empty command raises an exception")
  void testRunCommandThrowsForAnEmptyCommand() {
    assertThatThrownBy(() -> MavenInvoker.runCommand(List.of(), null))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("cannot be empty");
  }

  @Test
  @Timeout(60)
  @DisplayName("Test that a command flooding the standard error does not block")
  void testRunCommandDoesNotBlockOnALargeStandardError(@TempDir Path tempDir) throws Exception {
    // A process that writes much more to its standard error than the pipe buffer holds. If the
    // standard error is not drained while waiting for the process, both processes block forever.
    Path source = tempDir.resolve("FloodStandardError.java");
    Files.writeString(
        source,
        """
        public class FloodStandardError {
          public static void main(String[] args) {
            for (int i = 0; i < 200000; i++) {
              System.err.println("a line on the standard error, number " + i);
            }
            System.out.println("done");
          }
        }
        """);

    String[] output =
        MavenInvoker.runCommand(
            List.of(javaExecutable(), source.toAbsolutePath().toString()), null);

    assertThat(output).contains("done");
  }

  @Test
  @DisplayName("Test that arguments are not interpreted by a shell")
  void testRunCommandDoesNotInterpretArgumentsWithAShell(@TempDir Path tempDir) throws Exception {
    // The argument contains characters that a shell would expand. They must reach the process
    // unchanged, and no shell command must be executed.
    Path source = tempDir.resolve("EchoArgument.java");
    Files.writeString(
        source,
        """
        public class EchoArgument {
          public static void main(String[] args) {
            System.out.println(args[0]);
          }
        }
        """);
    String argument = "a value; with spaces $HOME `pwd` && echo injected";

    String[] output =
        MavenInvoker.runCommand(
            List.of(javaExecutable(), source.toAbsolutePath().toString(), argument), null);

    assertThat(output).containsExactly(argument);
  }

  @Test
  @Timeout(60)
  @DisplayName("Test that the whole standard output is returned")
  void testRunCommandReturnsTheWholeStandardOutput(@TempDir Path tempDir) throws Exception {
    int lines = 5000;
    Path source = tempDir.resolve("WriteManyLines.java");
    Files.writeString(
        source,
        """
        public class WriteManyLines {
          public static void main(String[] args) {
            for (int i = 0; i < %d; i++) {
              System.out.println("line " + i);
            }
          }
        }
        """
            .formatted(lines));

    String[] output =
        MavenInvoker.runCommand(
            List.of(javaExecutable(), source.toAbsolutePath().toString()), null);

    assertThat(output).hasSize(lines);
    assertThat(output[0]).isEqualTo("line 0");
    assertThat(output[lines - 1]).isEqualTo("line " + (lines - 1));
  }

  @Test
  @DisplayName("Test that the deprecated command as a single string is still executed")
  @SuppressWarnings("deprecation")
  void testRunCommandWithTheCommandAsASingleString() throws IOException, InterruptedException {
    String[] output = MavenInvoker.runCommand(javaExecutable() + " -version", null);

    // `java -version` writes to the standard error, so the standard output is empty, but the
    // command must be found and must succeed.
    assertThat(output).isEmpty();
  }

  @Test
  @Timeout(60)
  @DisplayName("Test that a failure quotes the last lines of the output and marks the truncation")
  void testRunCommandReportsTheTailOfTheOutputOfAFailedCommand(@TempDir Path tempDir)
      throws Exception {
    int lines = 500;
    Path source = tempDir.resolve("FailAfterWriting.java");
    Files.writeString(
        source,
        """
        public class FailAfterWriting {
          public static void main(String[] args) {
            for (int i = 0; i < %d; i++) {
              System.err.println("error line " + i);
            }
            System.exit(3);
          }
        }
        """
            .formatted(lines));

    assertThatThrownBy(
            () ->
                MavenInvoker.runCommand(
                    List.of(javaExecutable(), source.toAbsolutePath().toString()), null))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("terminated with exit code 3")
        .hasMessageContaining("only the last 200 lines are shown")
        // The end of the output is the interesting part and has to be kept, the beginning is
        // dropped.
        .hasMessageContaining("error line " + (lines - 1))
        .matches(t -> !t.getMessage().contains("error line 0" + System.lineSeparator()));
  }

  @Test
  @Timeout(120)
  @DisplayName("Test that an interrupted command ends the whole process tree")
  // S2925: the sleep polls for the child's readiness file, there is no event to wait on instead.
  @SuppressWarnings("java:S2925")
  void testRunCommandEndsTheProcessWhenInterrupted(@TempDir Path tempDir) throws Exception {
    Path readyFile = tempDir.resolve("ready");
    Path pidFile = tempDir.resolve("pid");
    // The process writes its pid and a ready marker, ignores the graceful termination through a
    // shutdown hook that sleeps, and then blocks. This exercises the forcible termination.
    Path source = tempDir.resolve("StubbornSleeper.java");
    Files.writeString(
        source,
        """
        import java.nio.file.Files;
        import java.nio.file.Path;
        public class StubbornSleeper {
          public static void main(String[] args) throws Exception {
            Runtime.getRuntime()
                .addShutdownHook(
                    new Thread(
                        () -> {
                          try {
                            Thread.sleep(600000);
                          } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                          }
                        }));
            Files.writeString(Path.of(args[0]), String.valueOf(ProcessHandle.current().pid()));
            Files.writeString(Path.of(args[1]), "ready");
            Thread.sleep(600000);
          }
        }
        """);

    AtomicReference<Throwable> failure = new AtomicReference<>();
    AtomicBoolean interruptStatusRestored = new AtomicBoolean();
    Thread caller =
        new Thread(
            () -> {
              try {
                MavenInvoker.runCommand(
                    List.of(
                        javaExecutable(),
                        source.toAbsolutePath().toString(),
                        pidFile.toAbsolutePath().toString(),
                        readyFile.toAbsolutePath().toString()),
                    null);
              } catch (Throwable t) {
                failure.set(t);
                interruptStatusRestored.set(Thread.currentThread().isInterrupted());
              }
            });
    caller.start();
    // Interrupt only once the process is actually running, so that the cleanup has something to do.
    long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);
    while (!Files.exists(readyFile) && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
    }
    assertThat(Files.exists(readyFile)).as("the process should have started").isTrue();
    long pid = Long.parseLong(Files.readString(pidFile).trim());

    caller.interrupt();
    caller.join(TimeUnit.SECONDS.toMillis(60));

    assertThat(caller.isAlive()).isFalse();
    assertThat(failure.get()).isInstanceOf(InterruptedException.class);
    assertThat(interruptStatusRestored.get()).isTrue();
    assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false))
        .as("the process should have been terminated")
        .isFalse();
  }

  private static String javaExecutable() {
    return Path.of(System.getProperty("java.home"), "bin", "java").toString();
  }

  @AfterAll
  public static void tearDown() throws IOException {
    if (producedTree.exists()) {
      FileUtils.forceDelete(producedTree);
    }
    if (nullDirectoryProducedTree.exists()) {
      FileUtils.forceDelete(nullDirectoryProducedTree);
    }
  }
}
