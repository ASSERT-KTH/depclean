package se.kth.depclean.core.analysis.src;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImportsAnalyzerTest {

  Path src = new File("src/test/resources/basic_spring_maven_project/src/main/java").toPath();
  Path srcTest = new File("src/test/resources/basic_spring_maven_project/src/test/java").toPath();

  @Test
  @DisplayName("All the imports in the source files are collected")
  void collectImportedClassesFromSource() {
    ImportsAnalyzer importsAnalyzer = new ImportsAnalyzer(src);
    Set<String> imports = importsAnalyzer.collectImportedClassesFromSource();
    Set<String> output = new HashSet<>();
    output.add("org.springframework.boot.SpringApplication");
    output.add("org.springframework.boot.autoconfigure.SpringBootApplication");
    Assertions.assertEquals(imports, output);
  }

  @Test
  @DisplayName("All the imports in the test files are collected")
  void collectImportedClassesFromTests() {
    ImportsAnalyzer importsAnalyzer = new ImportsAnalyzer(srcTest);
    Set<String> imports = importsAnalyzer.collectImportedClassesFromSource();
    Set<String> output = new HashSet<>();
    output.add("org.junit.jupiter.api.Test");
    output.add("org.springframework.boot.test.context.SpringBootTest");
    Assertions.assertEquals(imports, output);
  }
}