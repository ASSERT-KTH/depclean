package se.kth.depclean.core.analysis.src;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaClass;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

/** All the classes imported in the source code of the project. */
@Data
@AllArgsConstructor
@Slf4j
public class ImportsAnalyzer {
  private static final String[] SOURCE_FILE_EXTENSIONS = new String[] {"java"};

  /** A directory with Java source files. */
  private Path directoryPath;

  /**
   * Collects the set of all imported classes in all the Java source files in a directory.
   *
   * @return The set of all the imports.
   */
  public Set<String> collectImportedClassesFromSource() {
    if (!Files.isReadable(directoryPath) || !Files.isDirectory(directoryPath)) {
      return Collections.emptySet();
    }
    JavaProjectBuilder builder = constructJavaProjectBuilder();
    return extractImportsFrom(builder);
  }

  private JavaProjectBuilder constructJavaProjectBuilder() {
    JavaProjectBuilder builder = new JavaProjectBuilder();
    for (File file : FileUtils.listFiles(directoryPath.toFile(), SOURCE_FILE_EXTENSIONS, true)) {
      try {
        builder.addSource(file);
      } catch (IOException | RuntimeException e) {
        log.info("Cannot analyze imports in file: {}", file.getAbsolutePath());
      }
    }
    return builder;
  }

  private Set<String> extractImportsFrom(JavaProjectBuilder builder) {
    Set<String> imports = new HashSet<>();
    for (JavaClass javaClass : builder.getClasses()) {
      imports.addAll(javaClass.getSource().getImports());
    }
    return imports;
  }
}
