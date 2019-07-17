package se.kth.jdbl.pom.util;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class JarUtils {

    public static Map<File, List<String>> getMapOfJarAndTypes(File folder) {
        List<File> jarFiles = getJarsInFolder(folder);
        Map<File, List<String>> output = new HashMap<>();
        for (File jarFile : jarFiles) {
            output.put(jarFile, getTypesInJar(jarFile.getAbsolutePath()));
        }
        return output;
    }

    private static List<String> getTypesInJar(String jarFile) {
        List<String> listOfClasses = new ArrayList<>();
        try {
            JarInputStream jarInputStream = new JarInputStream(new FileInputStream(jarFile));
            JarEntry jarEntry;
            while (true) {
                jarEntry = jarInputStream.getNextJarEntry();
                if (jarEntry == null) {
                    break;
                }

                if ((jarEntry.getName().endsWith(".class"))) {
                    String className = jarEntry.getName().replaceAll(File.pathSeparator, "\\.");
                    String myClass = className.substring(0, className.lastIndexOf('.'));
                    listOfClasses.add(myClass);
                }
            }
        } catch (Exception e) {
            System.out.println("Oops.. Encounter an issue while parsing jar" + e.toString());
        }
        return listOfClasses;
    }

    private static List<File> getJarsInFolder(File folder) {
        List<File> jarFiles = new ArrayList<>();
        for (final File fileEntry : folder.listFiles()) {
            if (fileEntry.isDirectory()) {
                getJarsInFolder(fileEntry);
            } else {
                if (fileEntry.getName().endsWith(".jar")) {
                    jarFiles.add(fileEntry);
                }
            }
        }
        return jarFiles;
    }
}