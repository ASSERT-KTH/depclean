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

package se.kth.depclean.util;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utility class to execute Maven tasks from the command line. */
public final class MavenInvoker {

  private static final Logger log = LoggerFactory.getLogger(MavenInvoker.class);

  /** Number of lines of each stream that are quoted in the exception of a failed command. */
  private static final int MAX_REPORTED_LINES = 200;

  /** Time to wait for the process to end, and for its output to be read, during cleanup. */
  private static final long CLEANUP_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(10);

  /**
   * Time to wait after forcibly killing the process, so that the operating system can reap it. This
   * is guaranteed even after the graceful termination has used up the rest of the budget.
   */
  private static final long FORCIBLE_KILL_GRACE_MILLIS = TimeUnit.SECONDS.toMillis(2);

  private MavenInvoker() {}

  /**
   * Creates a native process to execute a custom command.
   *
   * @param cmd The command to be executed. It is split on whitespace, so it cannot contain
   *     arguments with spaces.
   * @param directory The working directory of the subprocess, or null if the subprocess should
   *     inherit the working directory of the current process.
   * @return The lines the command wrote to its standard output.
   * @throws IOException In case of IO issues, or if the command terminates with a non-zero exit
   *     code.
   * @throws InterruptedException If the current thread is interrupted while waiting for the
   *     command.
   * @deprecated use {@link #runCommand(List, File)} instead, which supports arguments containing
   *     spaces. Deprecated since 2.2.0.
   */
  @Deprecated
  public static String[] runCommand(String cmd, @Nullable File directory)
      throws IOException, InterruptedException {
    // The same tokenization Runtime.exec(String) used to apply to this command.
    List<String> command = new ArrayList<>();
    StringTokenizer tokenizer = new StringTokenizer(cmd);
    while (tokenizer.hasMoreTokens()) {
      command.add(tokenizer.nextToken());
    }
    return runCommand(command, directory);
  }

  /**
   * Creates a native process to execute a command. This method is used to invoke maven plugins
   * directly. On Unix the command is executed without a shell, so its arguments are passed to the
   * process verbatim and are never subject to expansion or word splitting.
   *
   * @param command The command and its arguments, e.g. {@code ["mvn", "dependency:tree"]}.
   * @param directory The working directory of the subprocess, or null if the subprocess should
   *     inherit the working directory of the current process.
   * @return The lines the command wrote to its standard output.
   * @throws IOException If the command cannot be started, if its output cannot be read completely,
   *     or if it terminates with a non-zero exit code.
   * @throws InterruptedException If the current thread is interrupted while waiting for the
   *     command.
   */
  // S2142 is suppressed here and in Cleanup: the interrupt is not swallowed but remembered and
  // re-raised after the cleanup, so that an interruption cannot leave the process or its reader
  // threads behind.
  @SuppressWarnings("java:S2142")
  public static String[] runCommand(List<String> command, @Nullable File directory)
      throws IOException, InterruptedException {
    if (command.isEmpty()) {
      throw new IOException("The command to execute cannot be empty");
    }
    List<String> executableCommand = commandForCurrentOs(command);
    String displayCommand = String.join(" ", command);

    List<String> stdoutLines = new ArrayList<>();
    BoundedLines stderrLines = new BoundedLines(MAX_REPORTED_LINES);
    AtomicReference<IOException> stdoutError = new AtomicReference<>();
    AtomicReference<IOException> stderrError = new AtomicReference<>();

    ProcessBuilder processBuilder = new ProcessBuilder(executableCommand);
    if (directory != null) {
      processBuilder.directory(directory);
    }
    Process process = processBuilder.start();
    // Both streams have to be drained concurrently: a process that writes more than the pipe
    // buffer holds to a stream nobody reads blocks forever, and so does the caller waiting for it.
    Thread stdoutReader =
        readStreamAsync(process.getInputStream(), stdoutLines::add, stdoutError, "out");
    Thread stderrReader =
        readStreamAsync(process.getErrorStream(), stderrLines::add, stderrError, "err");

    Cleanup cleanup = new Cleanup();
    int exitCode = -1;
    try {
      closeQuietly(process.getOutputStream());
      exitCode = process.waitFor();
    } catch (InterruptedException e) {
      cleanup.interrupted = true;
    } finally {
      // The cleanup budget only starts now, so that a long-running command does not eat into the
      // time given to drain the last of its output.
      cleanup.startDeadline();
      // The process has to be gone before its readers can reach the end of the streams.
      cleanup.endProcess(process);
      cleanup.endReader(stdoutReader, process.getInputStream());
      cleanup.endReader(stderrReader, process.getErrorStream());
      closeQuietly(process.getInputStream());
      closeQuietly(process.getErrorStream());
    }
    if (cleanup.interrupted) {
      // Restoring the interrupt status only now, so that the cleanup above could complete.
      Thread.currentThread().interrupt();
      throw new InterruptedException("Interrupted while executing '" + displayCommand + "'");
    }
    if (!cleanup.outputIsComplete) {
      throw new IOException(
          "Unable to read the complete output of '"
              + displayCommand
              + "', its output is still being written after "
              + CLEANUP_TIMEOUT_MILLIS
              + "ms");
    }

    rethrowStreamFailure(stdoutError.get(), "standard output", displayCommand);
    rethrowStreamFailure(stderrError.get(), "standard error", displayCommand);

    if (exitCode != 0) {
      throw new IOException(
          "The command '"
              + displayCommand
              + "' terminated with exit code "
              + exitCode
              + stderrLines.report("standard error")
              + BoundedLines.tailOf(stdoutLines, MAX_REPORTED_LINES).report("standard output"));
    }
    if (!stderrLines.isEmpty()) {
      log.debug("Standard error of '{}':{}", displayCommand, stderrLines.reportWithoutHeader());
    }
    return stdoutLines.toArray(new String[0]);
  }

  private static List<String> commandForCurrentOs(List<String> command) {
    if (!OsUtils.isWindows()) {
      return command;
    }
    // On Windows `mvn` is a batch script, which cannot be started by CreateProcess directly, so it
    // has to be launched through the command interpreter, as it was before as well.
    List<String> windowsCommand = new ArrayList<>(Arrays.asList("cmd", "/c"));
    windowsCommand.addAll(command);
    return windowsCommand;
  }

  private static Thread readStreamAsync(
      InputStream inputStream,
      Consumer<String> lineConsumer,
      AtomicReference<IOException> readError,
      String streamName) {
    Thread thread =
        new Thread(
            () -> {
              try (BufferedReader reader =
                  new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                  lineConsumer.accept(line);
                }
              } catch (IOException e) {
                readError.set(e);
              }
            },
            "depclean-process-" + streamName);
    thread.setDaemon(true);
    thread.start();
    return thread;
  }

  private static void rethrowStreamFailure(
      @Nullable IOException readError, String streamName, String command) throws IOException {
    if (readError != null) {
      throw new IOException(
          "Failed to read the " + streamName + " of '" + command + "'", readError);
    }
  }

  private static void closeQuietly(Closeable closeable) {
    try {
      closeable.close();
    } catch (IOException e) {
      log.debug("Unable to close a stream of the subprocess: {}", e.getMessage());
    }
  }

  /**
   * Ends the process and the threads reading its output, waiting for them but never longer than
   * {@link #CLEANUP_TIMEOUT_MILLIS}, plus a short {@link #FORCIBLE_KILL_GRACE_MILLIS} after a
   * forcible kill so the operating system can reap the process. Interruptions during the cleanup
   * are remembered instead of aborting it, so that the caller can restore the interrupt status once
   * everything is closed.
   */
  @SuppressWarnings("java:S2142")
  private static final class Cleanup {

    private long deadlineNanos;
    private boolean interrupted;
    private boolean outputIsComplete = true;

    /**
     * Starts the cleanup time budget. Called once the command itself has finished or was aborted.
     */
    private void startDeadline() {
      deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(CLEANUP_TIMEOUT_MILLIS);
    }

    private void endProcess(Process process) {
      if (!process.isAlive()) {
        return;
      }
      // Snapshot the descendants now: once the process is destroyed they can no longer be listed
      // through it, but the handles keep working. Empty on a Java 8 runtime, where descendants
      // cannot be enumerated and children are expected to end with their parent.
      List<Object> tree = ProcessHandles.treeOf(process);

      ProcessHandles.destroy(tree, false);
      process.destroy();
      if (awaitProcess(process, deadlineNanos) && awaitHandles(tree, deadlineNanos)) {
        return;
      }
      // The graceful termination did not work in time. Kill the whole tree, and always allow a
      // short extra window for the operating system to reap it, even if the budget is spent.
      ProcessHandles.destroy(tree, true);
      process.destroyForcibly();
      long killDeadlineNanos =
          Math.max(deadlineNanos, System.nanoTime())
              + TimeUnit.MILLISECONDS.toNanos(FORCIBLE_KILL_GRACE_MILLIS);
      if (!(awaitProcess(process, killDeadlineNanos) && awaitHandles(tree, killDeadlineNanos))) {
        log.warn(
            "The process of the command, or one of its child processes, did not end and had to "
                + "be abandoned");
      }
    }

    private void endReader(Thread reader, InputStream stream) {
      if (awaitThread(reader)) {
        return;
      }
      // The stream did not reach its end, most likely because the process left a child holding it
      // open. Closing it makes the reader fail and stop, but then the captured output cannot be
      // trusted: the reader was still blocked, so it had not read everything.
      outputIsComplete = false;
      closeQuietly(stream);
      awaitThread(reader);
    }

    /** Waits for the process, and reports whether it ended. */
    private boolean awaitProcess(Process process, long untilNanos) {
      while (process.isAlive()) {
        long remainingMillis = TimeUnit.NANOSECONDS.toMillis(untilNanos - System.nanoTime());
        if (remainingMillis <= 0) {
          return !process.isAlive();
        }
        try {
          process.waitFor(remainingMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
      return true;
    }

    /** Waits until all the process handles have ended or the deadline is reached. */
    private boolean awaitHandles(List<Object> handles, long untilNanos) {
      boolean allDead = true;
      for (Object handle : handles) {
        allDead &= awaitHandle(handle, untilNanos);
      }
      return allDead;
    }

    /** Waits for the process handle, and reports whether it ended. */
    private boolean awaitHandle(Object handle, long untilNanos) {
      while (ProcessHandles.isAlive(handle)) {
        long remainingMillis = TimeUnit.NANOSECONDS.toMillis(untilNanos - System.nanoTime());
        if (remainingMillis <= 0) {
          return !ProcessHandles.isAlive(handle);
        }
        try {
          // ProcessHandle has no timed wait, so poll it until the deadline, briefly enough to not
          // overshoot it.
          Thread.sleep(Math.min(remainingMillis, 50));
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
      return true;
    }

    /** Waits for the thread, and reports whether it ended. */
    private boolean awaitThread(Thread thread) {
      while (thread.isAlive()) {
        long remainingMillis = remainingMillis();
        if (remainingMillis <= 0) {
          return !thread.isAlive();
        }
        try {
          thread.join(remainingMillis);
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
      return true;
    }

    private long remainingMillis() {
      return TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
    }
  }

  /**
   * Reflective access to {@code ProcessHandle}, which only exists on Java 9+. The bytecode of this
   * class targets Java 8, but on the Java 9+ runtimes it actually runs on, the descendants of a
   * process can still be terminated: without this, killing a command wrapped in an interpreter
   * (e.g. {@code cmd /c} on Windows) would leave the actual process running. On a Java 8 runtime
   * every method is a no-op.
   */
  private static final class ProcessHandles {

    private static final @Nullable Method TO_HANDLE;
    private static final @Nullable Method DESCENDANTS;
    private static final @Nullable Method DESTROY;
    private static final @Nullable Method DESTROY_FORCIBLY;
    private static final @Nullable Method IS_ALIVE;

    static {
      Method toHandle = null;
      Method descendants = null;
      Method destroy = null;
      Method destroyForcibly = null;
      Method isAlive = null;
      try {
        Class<?> processHandle = Class.forName("java.lang.ProcessHandle");
        toHandle = Process.class.getMethod("toHandle");
        descendants = processHandle.getMethod("descendants");
        destroy = processHandle.getMethod("destroy");
        destroyForcibly = processHandle.getMethod("destroyForcibly");
        isAlive = processHandle.getMethod("isAlive");
      } catch (ReflectiveOperationException e) {
        // Java 8 runtime: ProcessHandle is not available
      }
      TO_HANDLE = toHandle;
      DESCENDANTS = descendants;
      DESTROY = destroy;
      DESTROY_FORCIBLY = destroyForcibly;
      IS_ALIVE = isAlive;
    }

    private ProcessHandles() {}

    /** The handles of the descendants of the process and of the process itself. */
    private static List<Object> treeOf(Process process) {
      if (TO_HANDLE == null || DESCENDANTS == null) {
        return Collections.emptyList();
      }
      try {
        Object handle = TO_HANDLE.invoke(process);
        List<Object> tree = new ArrayList<>();
        ((Stream<?>) DESCENDANTS.invoke(handle)).forEach(tree::add);
        tree.add(handle);
        return tree;
      } catch (ReflectiveOperationException | RuntimeException e) {
        log.debug("Unable to list the process tree: {}", e.getMessage());
        return Collections.emptyList();
      }
    }

    private static void destroy(List<Object> handles, boolean forcibly) {
      Method method = forcibly ? DESTROY_FORCIBLY : DESTROY;
      if (method == null) {
        return;
      }
      for (Object handle : handles) {
        try {
          method.invoke(handle);
        } catch (ReflectiveOperationException | RuntimeException e) {
          log.debug("Unable to terminate a process of the tree: {}", e.getMessage());
        }
      }
    }

    private static boolean isAlive(Object handle) {
      if (IS_ALIVE == null) {
        return false;
      }
      try {
        return Boolean.TRUE.equals(IS_ALIVE.invoke(handle));
      } catch (ReflectiveOperationException | RuntimeException e) {
        return false;
      }
    }
  }

  /** Keeps the last lines of a stream, so that a very chatty process cannot exhaust the heap. */
  private static final class BoundedLines {

    private final Deque<String> lines = new ArrayDeque<>();
    private final int maxLines;
    private boolean truncated;

    private BoundedLines(int maxLines) {
      this.maxLines = maxLines;
    }

    private static BoundedLines tailOf(List<String> allLines, int maxLines) {
      BoundedLines tail = new BoundedLines(maxLines);
      allLines.forEach(tail::add);
      return tail;
    }

    private synchronized void add(String line) {
      lines.addLast(line);
      if (lines.size() > maxLines) {
        lines.removeFirst();
        truncated = true;
      }
    }

    private synchronized boolean isEmpty() {
      return lines.isEmpty();
    }

    private String report(String streamName) {
      return header(streamName) + reportWithoutHeader();
    }

    private synchronized String reportWithoutHeader() {
      if (lines.isEmpty()) {
        return "";
      }
      StringBuilder report = new StringBuilder(System.lineSeparator());
      if (truncated) {
        report
            .append("[only the last ")
            .append(maxLines)
            .append(" lines are shown]")
            .append(System.lineSeparator());
      }
      report.append(String.join(System.lineSeparator(), lines));
      return report.toString();
    }

    private synchronized String header(String streamName) {
      if (lines.isEmpty()) {
        return "";
      }
      return System.lineSeparator() + streamName + ":";
    }
  }
}
