package se.kth.depclean.core.analysis.asmTest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.signature.SignatureVisitor;
import lombok.extern.slf4j.Slf4j;
import se.kth.depclean.core.analysis.asm.DefaultAnnotationVisitor;
import se.kth.depclean.core.analysis.asm.DefaultClassVisitor;
import se.kth.depclean.core.analysis.asm.DefaultFieldVisitor;
import se.kth.depclean.core.analysis.asm.DefaultMethodVisitor;
import se.kth.depclean.core.analysis.asm.DefaultSignatureVisitor;
import se.kth.depclean.core.analysis.asm.ResultCollector;
import se.kth.depclean.core.analysis.graph.ClassMembersVisitorCounter;

@Slf4j
public class ASMTest {

  // Resource class for testing.
  private static final File classFile = new File(
          "src/test/resources/asmAndGraphResources/ExampleClass.class");

  @Test
  @DisplayName("Test that the default asm classes are working fine.")
  void test_for_proper_asm_functioning() throws IOException {

    ResultCollector resultCollector = new ResultCollector();
    FileInputStream fileInputStream = new FileInputStream(classFile);
    ClassReader reader = new ClassReader(fileInputStream);

    AnnotationVisitor annotationVisitor = new DefaultAnnotationVisitor(resultCollector);
    SignatureVisitor signatureVisitor = new DefaultSignatureVisitor(resultCollector);
    FieldVisitor fieldVisitor = new DefaultFieldVisitor(
        annotationVisitor,
        resultCollector
    );
    MethodVisitor methodVisitor = new DefaultMethodVisitor(
        annotationVisitor,
        signatureVisitor,
        resultCollector
    );
    DefaultClassVisitor defaultClassVisitor = new DefaultClassVisitor(
        signatureVisitor,
        annotationVisitor,
        fieldVisitor,
        methodVisitor,
        resultCollector
    );

    reader.accept(defaultClassVisitor, 0);

    // Confirms that after analyzing there were no annotations in the test class.
    Assertions.assertEquals(0, ClassMembersVisitorCounter.getNbVisitedAnnotations());

    // Confirms that after analyzing there were some fields in the test class.
    Assertions.assertNotEquals(0, ClassMembersVisitorCounter.getNbVisitedFields());

    // Confirms that after analyzing there were some methods in the test class.
    Assertions.assertNotEquals(0, ClassMembersVisitorCounter.getNbVisitedMethods());

    // Confirms that after analyzing there were some types in the test class.
    Assertions.assertNotEquals(0, ClassMembersVisitorCounter.getNbVisitedTypes());

    // Confirms that the result of analyzed data has been collected successfully.
    Assertions.assertFalse(resultCollector.getDependencies().isEmpty());
  }
}
