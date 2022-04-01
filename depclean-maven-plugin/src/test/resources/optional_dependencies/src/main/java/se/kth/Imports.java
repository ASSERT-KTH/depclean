package se.kth;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaClass;
import java.io.File;
import java.util.Collection;
import java.util.List;

public class Imports {

  public static void main(String[] args) throws Exception {
    JavaProjectBuilder builder = new JavaProjectBuilder();
    // a directory
    builder.addSourceTree(new File("/Users/cesarsv/IdeaProjects/depclean/depclean-maven-plugin/src/test/resources/optional_dependencies"));
    Collection<JavaClass> javaClasses = builder.getClasses();
    for (JavaClass javaClass : javaClasses) {
      List<String> imports = javaClass.getSource().getImports();
      for (String imp : imports) {
        System.out.println(imp);
      }
    }
  }
}
