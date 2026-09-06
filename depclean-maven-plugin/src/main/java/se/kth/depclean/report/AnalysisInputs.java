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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Fingerprints what a DepClean analysis is computed from: the POM and the compiled class files. Two
 * runs over identical inputs yield the same fingerprint, so a stored snapshot can be trusted
 * whenever the fingerprint still matches, even if the class files were recompiled in between (a
 * forked lifecycle does that on some platforms) and therefore carry newer timestamps.
 */
public final class AnalysisInputs {

  private AnalysisInputs() {}

  /**
   * Computes the SHA-256 fingerprint of the POM and of every {@code .class} file under the given
   * directories. Missing directories contribute nothing.
   *
   * @param pom the project POM
   * @param classDirectories the compiled main and test class directories
   * @return a lowercase hexadecimal digest
   */
  public static String fingerprint(Path pom, Collection<Path> classDirectories) throws IOException {
    MessageDigest digest = sha256();
    digest.update(Files.readAllBytes(pom));
    for (Path directory : classDirectories) {
      if (!Files.isDirectory(directory)) {
        continue;
      }
      for (Path file : classFiles(directory)) {
        // The relative path makes renames and moves visible, not just content changes
        digest.update(directory.relativize(file).toString().getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(Files.readAllBytes(file));
      }
    }
    return hex(digest.digest());
  }

  private static List<Path> classFiles(Path directory) throws IOException {
    try (Stream<Path> files = Files.walk(directory)) {
      return files
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".class"))
          .sorted()
          .collect(Collectors.toList());
    } catch (UncheckedIOException e) {
      throw e.getCause();
    }
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is mandatory on every JVM", e);
    }
  }

  private static String hex(byte[] bytes) {
    StringBuilder builder = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      builder
          .append(Character.forDigit((b >> 4) & 0xF, 16))
          .append(Character.forDigit(b & 0xF, 16));
    }
    return builder.toString();
  }
}
