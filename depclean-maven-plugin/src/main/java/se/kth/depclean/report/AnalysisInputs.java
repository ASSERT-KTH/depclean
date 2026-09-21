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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.project.MavenProject;

/**
 * Fingerprints what a DepClean analysis is computed from: the POM, the compiled class files and the
 * coordinates of the resolved dependencies. Two runs over identical inputs yield the same
 * fingerprint, so a stored snapshot can be trusted whenever the fingerprint still matches, even if
 * the class files were recompiled in between (a forked lifecycle does that on some platforms) and
 * therefore carry newer timestamps. The resolved coordinates matter because dependency versions can
 * also come from a parent POM, an activated settings profile or a command line property, none of
 * which changes this project's POM.
 */
public final class AnalysisInputs {

  private AnalysisInputs() {}

  /**
   * The class directories an analysis actually reads. Test classes are only an input when tests are
   * analysed, so with {@code ignoreTests} they must not invalidate a stored snapshot either.
   *
   * @param classDirectory the compiled main class directory
   * @param testClassDirectory the compiled test class directory
   * @param ignoreTests whether the analysis skips the test classes
   * @return the directories to fingerprint
   */
  public static List<Path> classDirectories(
      Path classDirectory, Path testClassDirectory, boolean ignoreTests) {
    List<Path> directories = new ArrayList<>();
    directories.add(classDirectory);
    if (!ignoreTests) {
      directories.add(testClassDirectory);
    }
    return directories;
  }

  /**
   * The coordinates of the dependencies Maven resolved for the project, as {@code
   * groupId:artifactId:version:scope}. They are what an analysis reads from the dependency tree, so
   * they belong to its inputs: a version that comes from a parent POM, an activated settings
   * profile or a command line property changes the analysis even though this project's POM does not
   * change.
   *
   * @param project the Maven project whose resolved dependencies to describe
   * @return one coordinate per resolved dependency, in no particular order
   */
  public static List<String> resolvedCoordinates(MavenProject project) {
    return project.getArtifacts().stream()
        .map(
            artifact ->
                artifact.getGroupId()
                    + ":"
                    + artifact.getArtifactId()
                    + ":"
                    + artifact.getVersion()
                    + ":"
                    + artifact.getScope())
        .collect(Collectors.toList());
  }

  /**
   * Computes the SHA-256 fingerprint of the POM, of every {@code .class} file under the given
   * directories and of the given resolved dependency coordinates. Missing directories and an empty
   * coordinate list contribute nothing.
   *
   * @param pom the project POM
   * @param classDirectories the compiled main and test class directories
   * @param resolvedCoordinates the resolved dependency coordinates, in any order
   * @return a lowercase hexadecimal digest
   */
  public static String fingerprint(
      Path pom, Collection<Path> classDirectories, Collection<String> resolvedCoordinates)
      throws IOException {
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
    // Sorted, so that a different iteration order of the dependency graph does not invalidate a
    // snapshot that describes the very same dependencies
    for (String coordinate : resolvedCoordinates.stream().sorted().collect(Collectors.toList())) {
      digest.update(coordinate.getBytes(StandardCharsets.UTF_8));
      digest.update((byte) 0);
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
