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

package se.kth.depclean.core.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

/** Utility class to handle JAR files. */
@Slf4j
public final class JarUtils {

  /** Size of the buffer to read/write data. */
  private static final int BUFFER_SIZE = 16384;
  private static final int MAX_ENTRIES = 10_000;
  private static final long MAX_TOTAL_UNCOMPRESSED_SIZE = 1_000_000_000L;
  private static final double MAX_COMPRESSION_RATIO = 100.0;
  private static final int MAX_ENTRY_NAME_LENGTH = 1000;

  private JarUtils() {}

  /**
   * Decompress all JAR files located in a given directory.
   *
   * @param outputDirectory The directory path to put the decompressed files.
   */
  public static void decompress(final String outputDirectory) {
    File files = new File(outputDirectory);
    for (File f : Objects.requireNonNull(files.listFiles())) {
      if (isArchive(f.getName())) {
        try {
          JarUtils.decompressDependencyFiles(f.getAbsolutePath());
          // delete the original dependency jar file
          FileUtils.forceDelete(f);
        } catch (IOException e) {
          log.warn("Problem decompressing jar file: " + f.getAbsolutePath());
          log.error(e.getMessage(), e);
        }
      }
    }
  }

  /**
   * Decompress all JAR/WAR/EAR files withing a file (recursively).
   *
   * @param zipFile The file to be decompressed.
   */
  private static void decompressDependencyFiles(String zipFile) throws IOException {
    File file = new File(zipFile);
    try (ZipFile zip = new ZipFile(file)) {
      extractEntries(zip, createOutputDirectory(zipFile));
    }
  }

  private static String createOutputDirectory(String zipFile) {
    String newPath = zipFile.substring(0, zipFile.length() - 4);
    new File(newPath).mkdir();
    return newPath;
  }

  private static void extractEntries(ZipFile zip, String outputDirectory) throws IOException {
    Enumeration<? extends ZipEntry> zipFileEntries = zip.entries();
    ExtractionProgress progress = new ExtractionProgress();

    while (zipFileEntries.hasMoreElements() && progress.entryCount < MAX_ENTRIES) {
      extractEntry(zip, outputDirectory, zipFileEntries.nextElement(), progress);
    }

    validateEntryLimit(progress.entryCount);
  }

  private static void extractEntry(
      ZipFile zip, String outputDirectory, ZipEntry entry, ExtractionProgress progress)
      throws IOException {
    String currentEntry = entry.getName();
    progress.entryCount++;

    if (hasSuspiciousName(currentEntry)) {
      return;
    }

    File destFile = resolveDestinationFile(outputDirectory, currentEntry);
    createParentDirectories(destFile);
    writeEntry(zip, entry, destFile, progress);
    decompressNestedArchive(currentEntry, destFile);
  }

  private static boolean hasSuspiciousName(String entryName) {
    return entryName.length() > MAX_ENTRY_NAME_LENGTH;
  }

  private static File resolveDestinationFile(String outputDirectory, String currentEntry)
      throws IOException {
    File destFile = new File(outputDirectory, currentEntry);
    // Sonar javasecurity:S6096
    if (!destFile.getCanonicalPath().startsWith(new File(outputDirectory).getCanonicalPath())) {
      throw new IOException("Entry is outside of the target directory");
    }
    return destFile;
  }

  private static void createParentDirectories(File destFile) {
    File destinationParent = destFile.getParentFile();
    destinationParent.mkdirs();
  }

  private static void writeEntry(
      ZipFile zip, ZipEntry entry, File destFile, ExtractionProgress progress) throws IOException {
    if (entry.isDirectory() || destFile.isDirectory()) {
      return;
    }

    try (BufferedInputStream is = new BufferedInputStream(zip.getInputStream(entry));
        FileOutputStream fos = new FileOutputStream(destFile);
        BufferedOutputStream dest = new BufferedOutputStream(fos, BUFFER_SIZE)) {
      writeEntryContent(entry, entry.getName(), is, dest, progress);
      dest.flush();
    }
  }

  private static void writeEntryContent(
      ZipEntry entry,
      String currentEntry,
      BufferedInputStream is,
      BufferedOutputStream dest,
      ExtractionProgress progress)
      throws IOException {
    int currentByte;
    byte[] data = new byte[BUFFER_SIZE];
    long entrySizeUncompressed = 0;

    while ((currentByte = is.read(data, 0, BUFFER_SIZE)) != -1) {
      dest.write(data, 0, currentByte);
      entrySizeUncompressed += currentByte;
      progress.totalSizeUncompressed += currentByte;
      validateCompressionRatio(entry, currentEntry, entrySizeUncompressed);
      validateTotalSize(progress.totalSizeUncompressed);
    }
  }

  private static void validateCompressionRatio(
      ZipEntry entry, String currentEntry, long entrySizeUncompressed) throws IOException {
    if (entry.getCompressedSize() <= 0) {
      return;
    }

    double compressionRatio = (double) entrySizeUncompressed / entry.getCompressedSize();
    if (compressionRatio > MAX_COMPRESSION_RATIO) {
      throw new IOException(
          "ZIP bomb detected: compression ratio too high for entry " + currentEntry);
    }
  }

  private static void validateTotalSize(long totalSizeUncompressed) throws IOException {
    if (totalSizeUncompressed > MAX_TOTAL_UNCOMPRESSED_SIZE) {
      throw new IOException("ZIP bomb detected: total uncompressed size exceeds limit");
    }
  }

  private static void decompressNestedArchive(String currentEntry, File destFile)
      throws IOException {
    if (!isArchive(currentEntry)) {
      return;
    }

    decompressDependencyFiles(destFile.getAbsolutePath());
    FileUtils.forceDelete(new File(destFile.getAbsolutePath()));
  }

  private static void validateEntryLimit(int entryCount) throws IOException {
    if (entryCount >= MAX_ENTRIES) {
      throw new IOException("ZIP bomb detected: too many entries in archive (" + entryCount + ")");
    }
  }

  private static boolean isArchive(String fileName) {
    return fileName.endsWith(".jar") || fileName.endsWith(".war") || fileName.endsWith(".ear");
  }

  private static class ExtractionProgress {
    private int entryCount;
    private long totalSizeUncompressed;
  }
}
