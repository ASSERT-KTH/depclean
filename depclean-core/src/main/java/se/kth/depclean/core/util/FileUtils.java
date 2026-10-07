/*
 * Copyright (c) 2020-2026, CASTOR Software Research Centre (www.castor.kth.se)
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

package se.kth.depclean.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** Utility class to handle files and directories. */
public final class FileUtils {

  private FileUtils() {}

  /**
   * Deletes a directory recursively.
   *
   * @param directory The directory to be deleted.
   * @throws IOException In case of IO issues.
   */
  public static void deleteDirectory(final File directory) throws IOException {
    if (!directory.exists()) {
      return;
    }
    if (!isSymlink(directory)) {
      cleanDirectory(directory);
    }
    Files.delete(directory.toPath());
  }

  private static boolean isSymlink(final File file) {
    if (file == null) {
      throw new NullPointerException("File must not be null");
    }
    return Files.isSymbolicLink(file.toPath());
  }

  private static void cleanDirectory(final File directory) throws IOException {
    final File[] files = verifiedListFiles(directory);
    IOException exception = null;
    for (final File file : files) {
      try {
        forceDelete(file);
      } catch (final IOException ioe) {
        exception = ioe;
      }
    }
    if (exception != null) {
      throw exception;
    }
  }

  private static File[] verifiedListFiles(final File directory) throws IOException {
    if (!directory.exists()) {
      throw new IllegalArgumentException(directory + " does not exist");
    }
    if (!directory.isDirectory()) {
      throw new IllegalArgumentException(directory + " is not a directory");
    }
    final File[] files = directory.listFiles();
    if (files == null) { // null if security restricted
      throw new IOException("Failed to list contents of " + directory);
    }
    return files;
  }

  /**
   * Delete a file or a directory recursively.
   *
   * @param file The file or directory to be deleted.
   * @throws IOException In case of IO issues.
   */
  public static void forceDelete(final File file) throws IOException {
    if (file.isDirectory()) {
      deleteDirectory(file);
    } else {
      Files.delete(file.toPath());
    }
  }
}
