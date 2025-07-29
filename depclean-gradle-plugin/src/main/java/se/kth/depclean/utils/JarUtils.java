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

package se.kth.depclean.utils;

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

  private JarUtils() {}

  /**
   * Decompress all JAR files located in a given directory.
   *
   * @param outputDirectory The directory path to put the decompressed files.
   */
  public static void decompress(final String outputDirectory) {
    File files = new File(outputDirectory);
    for (File f : Objects.requireNonNull(files.listFiles())) {
      if (f.getName().endsWith(".jar")
          || f.getName().endsWith(".war")
          || f.getName().endsWith(".ear")) {
        try {
          JarUtils.decompressDependencyFiles(f.getAbsolutePath());
          // delete the original dependency jar file
          FileUtils.forceDelete(f);
        } catch (IOException e) {
          log.warn("Problem decompressing jar file: " + f.getAbsolutePath());
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
      String newPath = zipFile.substring(0, zipFile.length() - 4);
      new File(newPath).mkdir();
      Enumeration<? extends ZipEntry> zipFileEntries = zip.entries();

      // Protection against ZIP bomb attacks
      int maxEntries = 10_000; // Maximum number of entries to process
      long maxTotalSize = 1_000_000_000L; // 1 GB maximum total uncompressed size
      double maxCompressionRatio = 100.0; // Maximum compression ratio

      int entryCount = 0;
      long totalSizeUncompressed = 0;

      // Process each entry
      while (zipFileEntries.hasMoreElements() && entryCount < maxEntries) {
        // grab a zip file entry
        ZipEntry entry = zipFileEntries.nextElement();
        String currentEntry = entry.getName();
        entryCount++;

        // Skip entries with suspicious characteristics
        if (currentEntry.length() > 1000) { // Skip entries with very long names
          continue;
        }

        File destFile = new File(newPath, currentEntry);
        File destinationParent = destFile.getParentFile();
        // create the parent directory structure if needed
        destinationParent.mkdirs();
        if (!entry.isDirectory()) {
          try (BufferedInputStream is = new BufferedInputStream(zip.getInputStream(entry));
              FileOutputStream fos = new FileOutputStream(destFile);
              BufferedOutputStream dest = new BufferedOutputStream(fos, BUFFER_SIZE)) {

            int currentByte;
            // establish buffer for writing file
            byte[] data = new byte[BUFFER_SIZE];
            long entrySizeUncompressed = 0;

            // read and write until last byte is encountered
            while ((currentByte = is.read(data, 0, BUFFER_SIZE)) != -1) {
              dest.write(data, 0, currentByte);
              entrySizeUncompressed += currentByte;
              totalSizeUncompressed += currentByte;

              // Check compression ratio for this entry
              if (entry.getCompressedSize() > 0) {
                double compressionRatio =
                    (double) entrySizeUncompressed / entry.getCompressedSize();
                if (compressionRatio > maxCompressionRatio) {
                  throw new IOException(
                      "ZIP bomb detected: compression ratio too high for entry " + currentEntry);
                }
              }

              // Check total uncompressed size
              if (totalSizeUncompressed > maxTotalSize) {
                throw new IOException("ZIP bomb detected: total uncompressed size exceeds limit");
              }
            }
            dest.flush();
            is.close();
          }
        }
        if (currentEntry.endsWith(".jar")
            || currentEntry.endsWith(".war")
            || currentEntry.endsWith(".ear")) {
          // found a zip file, try to open
          decompressDependencyFiles(destFile.getAbsolutePath());
          FileUtils.forceDelete(new File(destFile.getAbsolutePath()));
        }
      }

      // Check if processing was truncated due to too many entries
      if (entryCount >= maxEntries) {
        throw new IOException(
            "ZIP bomb detected: too many entries in archive (" + entryCount + ")");
      }
    }
  }
}
