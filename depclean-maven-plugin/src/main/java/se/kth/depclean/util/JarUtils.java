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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class to handle JAR files.
 */
@Slf4j
public final class JarUtils {

  /**
   * Size of the buffer to read/write data.
   */
  private static final int BUFFER_SIZE = 16384;

  private JarUtils() {
  }

  /**
   * Decompress all JAR files located in a given directory.
   *
   * @param outputDirectory The directory path to put the decompressed files.
   */
  public static void decompressJars(final String outputDirectory) {
    File files = new File(outputDirectory);
    for (File f : Objects.requireNonNull(files.listFiles())) {
      if (f.getName().endsWith(".jar")) {
        try {
          JarUtils.decompressJarFile(outputDirectory, f.getAbsolutePath());
          // delete the original dependency jar file
          org.apache.commons.io.FileUtils.forceDelete(f);
        } catch (IOException e) {
          log.warn("Problem decompressing jar file: " + f.getAbsolutePath());
        }
      }
    }
  }

  /**
   * Decompress a JAR file in a path to a directory (will be created if it doesn't exists).
   *
   * @param destDirectory The destine directory.
   * @param jarFilePath   The path to the Jar file to be decompressed.
   * @throws IOException In case of IO issues.
   */
  private static void decompressJarFile(String destDirectory, String jarFilePath) throws IOException {
    File destDir = new File(destDirectory);
    if (!destDir.exists()) {
      destDir.mkdir();
    }
    try (JarInputStream jarIn = new JarInputStream(new FileInputStream(jarFilePath))) {
      JarEntry entry = jarIn.getNextJarEntry();
      // iterates over all the entries in the jar file
      while (entry != null) {
        String filePath = destDirectory + File.separator + entry.getName();
        if (!entry.isDirectory()) {
          new File(filePath).getParentFile().mkdirs(); //NOSONAR Triggers a false warning of path traversal attack
          try {
            // if the entry is a file, extracts it
            extractFile(jarIn, filePath);
          } catch (IOException e) {
            log.warn("Could not extract file: " + filePath + " from jar " + jarFilePath);
          }
        }
        jarIn.closeEntry();
        entry = jarIn.getNextJarEntry();
      }
    }
  }

  /**
   * Extracts an entry file.
   *
   * @param jarIn    The jar file to be extracted.
   * @param filePath Path to the file.
   * @throws IOException In case of IO issues.
   */
  private static void extractFile(final JarInputStream jarIn, final String filePath) throws IOException {
    try (BufferedOutputStream bos = new BufferedOutputStream(
        new FileOutputStream(filePath))) { //NOSONAR Triggers a false warning of path traversal attack
      byte[] bytesIn = new byte[BUFFER_SIZE];
      int read;
      while ((read = jarIn.read(bytesIn)) != -1) {
        bos.write(bytesIn, 0, read);
      }
    }
  }
}
