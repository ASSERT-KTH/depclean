package se.kth.depclean.core.analysis;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.plexus.util.DirectoryScanner;

/**
 * Utility to visit classes in a library given either as a jar file or an exploded directory.
 */
@Slf4j
public final class ClassFileVisitorUtils {

  private static final String[] CLASS_INCLUDES = {"**/*.class"};
  public static final String CLASS = ".class";

  /**
   * Ctor.
   */
  private ClassFileVisitorUtils() {
    // private constructor for utility class
  }

  /**
   * Accepts a jar or directory to be analyzed.
   *
   * @param url     The jar or directory
   * @param visitor A {@link ClassFileVisitor}.
   */
  public static void accept(URL url, ClassFileVisitor visitor) {
    if (url.getPath().endsWith(".jar")) {
      acceptJar(url, visitor);
    } else {
      final String message = "Cannot accept visitor on URL: ";
      if (url.getProtocol().equalsIgnoreCase("file")) {
        try {
          File file = new File(new URI(url.toString()));
          if (file.isDirectory()) {
            acceptDirectory(file, visitor);
          } else if (file.exists()) {
            throw new IllegalArgumentException(message + url + " because file is not a directory.");
          }
        } catch (URISyntaxException exception) {
          throw new IllegalArgumentException(message + url, exception);
        }
      } else {
        throw new IllegalArgumentException(message + url + " because url isn't pointing a file.");
      }
    }
  }

  /**
   * Accepts a jar to be analyzed.
   *
   * @param url     URL of jar
   * @param visitor A {@link ClassFileVisitor}.
   */
  private static void acceptJar(URL url, ClassFileVisitor visitor) {
    try (JarInputStream in = new JarInputStream(url.openStream())) {
      JarEntry entry;
      while ((entry = in.getNextJarEntry()) != null) { //NOSONAR
        String name = entry.getName();
        // ignore files like package-info.class and module-info.class
        if (name.endsWith(CLASS) && name.indexOf('-') == -1) {
          visitClass(name, in, visitor);
        }
      }
    } catch (IOException e) {
      log.error("Error analyzing JAR: " + url);
    }
  }

  /**
   * Accepts a directory to be analyzed.
   *
   * @param directory Directory or File to be analyzed.
   * @param visitor   A {@link ClassFileVisitor}.
   */
  private static void acceptDirectory(File directory, ClassFileVisitor visitor) {
    if (!directory.isDirectory()) {
      throw new IllegalArgumentException("File is not a directory");
    }
    DirectoryScanner scanner = new DirectoryScanner();
    scanner.setBasedir(directory);
    scanner.setIncludes(CLASS_INCLUDES);
    scanner.scan();
    String[] paths = scanner.getIncludedFiles();
    for (String path : paths) {
      File file = new File(directory, path);
      try (FileInputStream in = new FileInputStream(file)) {
        visitClass(path, in, visitor);
      } catch (IOException e) {
        log.error("Error analyzing directory: " + directory.getAbsolutePath());
      }
    }
  }

  /**
   * Removes the root folder of the dependency from the path.
   *
   * @param path the dependency folder
   * @return path without the dependency folder
   */
  public static String getChild(String path) {
    Path tmp = Paths.get(path);
    if (tmp.getNameCount() > 1) {
      return tmp.subpath(1, tmp.getNameCount()).toString();
    } else {
      // impossible to extract child's path
      return path;
    }
  }

  /**
   * Visits the classes.
   *
   * @param path    Path of the class.
   * @param in      read the input bytes.
   * @param visitor A {@link ClassFileVisitor}.
   */
  private static void visitClass(String path, InputStream in, ClassFileVisitor visitor) {
    if (!path.endsWith(CLASS)) {
      throw new IllegalArgumentException("Path is not a class");
    }
    path = getChild(path);
    String className = path.substring(0, path.length() - CLASS.length());
    className = className.replace(File.separatorChar, '.');
    visitor.visitClass(className, in);
  }

}
