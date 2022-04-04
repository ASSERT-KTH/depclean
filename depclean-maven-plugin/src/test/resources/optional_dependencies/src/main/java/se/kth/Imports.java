package se.kth;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaClass;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Collection;
import java.util.List;
import org.apache.commons.io.FileUtils;

public class Imports {

  public static void main(String[] args) throws Exception {
    extracted2();
  }

  private static void extracted1() {
    JavaProjectBuilder builder = new JavaProjectBuilder();
    // a directory
    File directory = new File("/Users/cesarsv/IdeaProjects/depclean/depclean-maven-plugin/src/test/resources/optional_dependencies");
    builder.addSourceTree(directory);
    Collection<JavaClass> javaClasses = builder.getClasses();
    for (JavaClass javaClass : javaClasses) {
      List<String> imports = javaClass.getSource().getImports();
      for (String imp : imports) {
        System.out.println(imp);
      }
    }
  }



  private static void extracted2() {
    JavaProjectBuilder builder = new JavaProjectBuilder();
    // a directory
    File directory = new File("/Users/cesarsv/IdeaProjects/depclean/depclean-maven-plugin/src/test/resources/optional_dependencies");
    // get list of java files
    String[] extensions = new String[] { "java" };
    List<File> files = (List<File>) FileUtils.listFiles(directory, extensions, true);
    // build the sources tree
    for (File file : files) {
      try {
        builder.addSource(file);
      } catch (IOException | RuntimeException e) {
        //e.printStackTrace();
      }
    }
    Collection<JavaClass> javaClasses = builder.getClasses();
    for (JavaClass javaClass : javaClasses) {
      List<String> imports = javaClass.getSource().getImports();
      for (String imp : imports) {
        System.out.println(imp);
      }
    }
  }
}
