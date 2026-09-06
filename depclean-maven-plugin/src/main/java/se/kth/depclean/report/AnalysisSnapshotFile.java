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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Reads and writes the {@code depclean-analysis.json} file that lets {@code depclean:report} reuse
 * the analysis produced earlier in the build by {@code depclean:depclean}.
 */
public final class AnalysisSnapshotFile {

  /** Name of the file, located in the project build directory. */
  public static final String FILE_NAME = "depclean-analysis.json";

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private final Path file;

  /** Creates the accessor for the snapshot in the given build directory. */
  public AnalysisSnapshotFile(Path buildDirectory) {
    this.file = buildDirectory.resolve(FILE_NAME);
  }

  public Path getPath() {
    return file;
  }

  /** Writes the snapshot, replacing any previous one. */
  public void write(AnalysisSnapshot snapshot) throws IOException {
    Files.createDirectories(file.getParent());
    try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
      GSON.toJson(snapshot, writer);
    }
  }

  /**
   * Returns the stored snapshot if it can still be trusted: it exists, was produced with the same
   * settings, and is not older than the POM or any compiled class it was computed from.
   *
   * @param settings the settings the caller would analyze with
   * @param pom the project POM
   * @param classDirectories the compiled main and test class directories
   */
  public Optional<AnalysisSnapshot> readIfFresh(
      AnalysisSnapshot.Settings settings, Path pom, Collection<Path> classDirectories)
      throws IOException {
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    FileTime snapshotTime = Files.getLastModifiedTime(file);
    if (isNewerThan(pom, snapshotTime)) {
      return Optional.empty();
    }
    for (Path directory : classDirectories) {
      if (Files.isDirectory(directory) && containsFileNewerThan(directory, snapshotTime)) {
        return Optional.empty();
      }
    }
    AnalysisSnapshot snapshot = read();
    if (snapshot == null || !settings.equals(snapshot.getSettings())) {
      return Optional.empty();
    }
    return Optional.of(snapshot);
  }

  @Nullable
  private AnalysisSnapshot read() throws IOException {
    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      return GSON.fromJson(reader, AnalysisSnapshot.class);
    } catch (JsonParseException e) {
      // A corrupt or incompatible file is simply not reusable
      return null;
    }
  }

  private static boolean containsFileNewerThan(Path directory, FileTime time) throws IOException {
    try (Stream<Path> files = Files.walk(directory)) {
      return files.filter(Files::isRegularFile).anyMatch(path -> isNewerThan(path, time));
    }
  }

  private static boolean isNewerThan(Path path, FileTime time) {
    try {
      return Files.getLastModifiedTime(path).compareTo(time) > 0;
    } catch (IOException e) {
      return true;
    }
  }
}
