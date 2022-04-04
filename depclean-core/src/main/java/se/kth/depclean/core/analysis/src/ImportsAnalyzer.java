package se.kth.depclean.core.analysis.src;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaClass;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

/**
 * All the classes imported in the source code of the project.
 */
@Data
@AllArgsConstructor
@Slf4j
public class ImportsAnalyzer {

  /**
   * A directory with Java source files.
   */
  private Path directoryPath;

  /**
   * Collects the set of all imported classes in all the Java source files in a directory.
   *
   * @return The set of all the imports.
   */
  public Set<String> collectImportedClassesFromSource() {
    Set<String> importsSet = new HashSet<>();
    JavaProjectBuilder builder = new JavaProjectBuilder();
    String[] extensions = new String[]{"java"};
    File directory = new File(directoryPath.toUri());
    if (directory.canRead() && directory.isDirectory()) {
      List<File> files = (List<File>) FileUtils.listFiles(directoryPath.toFile(), extensions, true);
      for (File file : files) {
        try {
          builder.addSource(file);
        } catch (IOException | RuntimeException e) {
          log.info("Cannot analyze imports in file: " + file.getAbsolutePath());
        }
      }
      Collection<JavaClass> javaClasses = builder.getClasses();
      for (JavaClass javaClass : javaClasses) {
        List<String> imports = javaClass.getSource().getImports();
        importsSet.addAll(imports);
      }
    }
    return importsSet;
  }
}
