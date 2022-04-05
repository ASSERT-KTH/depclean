package se.kth.depclean.core.analysis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClassFileVisitorUtilsTest {

  @BeforeEach
  void setUp() {
  }

  @AfterEach
  void tearDown() {
  }

  @Test
  void dummy() throws Exception {
    Assertions.assertEquals("acd/cde", ClassFileVisitorUtils.getChild("tmp/acd/cde"));
  }

}