package se.kth.depclean.core.analysis;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ClassFileVisitorUtilsTest {

  @Test
  void testGetChild() {
    Path parent = Paths.get("Users", "Documents", "SVG");
    Path child = Paths.get("Documents", "SVG");
    Assertions.assertEquals(child.toString(), ClassFileVisitorUtils.getChild(parent.toString()));
  }
}
