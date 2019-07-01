package se.kth.jdbl.pom.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarUtils {

    /**
     * This method decompresses a jar file into a given destination.
     *
     * @param destinationDir
     * @param jarPath
     * @throws IOException
     */
    public static void decompressJarFile(String destinationDir, String jarPath) throws IOException {

        File file = new File(jarPath);

        if (file.exists()) {

            JarFile jar = new JarFile(file);
            // fist get all directories,
            // then make those directory on the destination path
            for (Enumeration<JarEntry> enums = jar.entries(); enums.hasMoreElements(); ) {
                JarEntry entry = (JarEntry) enums.nextElement();
                String fileName = destinationDir + File.separator + entry.getName();
                File f = new File(fileName);
                if (fileName.endsWith("/")) {
                    f.mkdirs();
                }
            }
            //now create all files
            for (Enumeration<JarEntry> enums = jar.entries(); enums.hasMoreElements(); ) {
                JarEntry entry = (JarEntry) enums.nextElement();
                String fileName = destinationDir + File.separator + entry.getName();
                File f = new File(fileName);
                if (!fileName.endsWith("/")) {
                    InputStream is = jar.getInputStream(entry);
                    FileOutputStream fos = new FileOutputStream(f);
                    // write contents of 'is' to 'fos'
                    while (is.available() > 0) {
                        fos.write(is.read());
                    }
                    fos.close();
                    is.close();
                }
            }
        }
    }
}
