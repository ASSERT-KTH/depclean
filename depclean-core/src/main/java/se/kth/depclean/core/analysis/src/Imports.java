package se.kth.depclean.core.analysis.src;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaClass;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * All the classes imported in the source code of the project.
 */
@Data
@AllArgsConstructor
public class Imports {

  /**
   * A directory with Java source files.
   */
  private Path directory;

  /**
   * Collects the set of all imports in all the Java source files in a directory.
   *
   * @return The set of all the imports.
   */
  public Set<String> collectImportedClassesFromSource() {
    Set<String> importsSet = new HashSet<>();
    try {
      JavaProjectBuilder builder = new JavaProjectBuilder();
      builder.addSourceTree(directory.toFile());
      Collection<JavaClass> javaClasses = builder.getClasses();
      for (JavaClass javaClass : javaClasses) {
        List<String> imports = javaClass.getSource().getImports();
        importsSet.addAll(imports);
      }
    } catch (Exception e) {
      System.out.println(e.getStackTrace());
    }
    return importsSet;
  }
}
