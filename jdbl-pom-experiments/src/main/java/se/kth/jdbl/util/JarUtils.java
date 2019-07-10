package se.kth.jdbl.util;

import java.io.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class JarUtils {

    /**
     * Size of the buffer to read/write data.
     */
    private static final int BUFFER_SIZE = 16384;

    private JarUtils() {
    }

    /**
     * Decompress all jar files located in a given directory.
     *
     * @param outputDirectory The path to the output directory
     */
    public static void decompressJars(String outputDirectory) {
        File files = new File(outputDirectory);
        for (File f : files.listFiles()) {
            if (f.getName().endsWith(".jar")) {
                try {
                    JarUtils.decompressJarFile(outputDirectory, f.getAbsolutePath());
                    // delete the original dependency jar file
                    f.delete();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Decompress a jar file in a path to a directory (will be created if it doesn't exists).
     *
     * @param jarFilePath
     * @param destDirectory
     * @throws IOException
     */
    public static void decompressJarFile(String destDirectory, String jarFilePath) throws IOException {
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
           destDir.mkdir();
        }
        JarInputStream jarIn = new JarInputStream(new FileInputStream(jarFilePath));
        JarEntry entry = jarIn.getNextJarEntry();
        // iterates over entries in the jar file
        while (entry != null) {
            String filePath = destDirectory + "/" + entry.getName();
            if (!entry.isDirectory()) {
                new File(filePath).getParentFile().mkdirs();
                // if the entry is a file, extracts it
                extractFile(jarIn, filePath);
            }/* else {
                System.out.println("New dir: " + filePath);
                // if the entry is a directory, make the directory
                File dir = new File(filePath);
                dir.mkdir();
                System.out.println(dir.canWrite());
            }*/
            jarIn.closeEntry();
            entry = jarIn.getNextJarEntry();
        }
        jarIn.close();
    }

    /**
     * Extract an entry file.
     *
     * @param jarIn
     * @param filePath
     * @throws IOException
     */
    private static void extractFile(JarInputStream jarIn, String filePath) throws IOException {
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
        byte[] bytesIn = new byte[BUFFER_SIZE];
        int read = 0;
        while ((read = jarIn.read(bytesIn)) != -1) {
            bos.write(bytesIn, 0, read);
        }
        bos.close();
    }
}
