package se.kth.depclean.core.analysis.asm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import se.kth.depclean.core.analysis.asm.ClassReferenceExtractor.Depth;
import se.kth.depclean.core.testing.Bytecode;

class ClassReferenceExtractorTest {

  @TempDir Path temp;

  private Set<String> referencesOf(String className, Depth depth, String... sources)
      throws IOException {
    Path classes = Bytecode.compile(temp.resolve("classes"), Collections.emptyList(), sources);
    return ClassReferenceExtractor.extract(
        Files.readAllBytes(classes.resolve(className.replace('.', '/') + ".class")), depth);
  }

  @Test
  void findsTheTypesTheBytecodeLinksAgainst() throws IOException {
    Set<String> references =
        referencesOf(
            "p.Subject",
            Depth.LINKAGE,
            "package p; public class Subject extends java.util.ArrayList<String>"
                + " implements java.io.Closeable {"
                + "  java.util.concurrent.atomic.AtomicInteger field;"
                + "  public void close() throws java.io.IOException {"
                + "    Object o = new java.util.Random();"
                + "    if (o instanceof java.util.Date) { System.out.println((Runnable) o); }"
                + "    try { o.wait(); } catch (InterruptedException e) { }"
                + "    Class<?> c = java.util.BitSet.class;"
                + "    Object[] a = new java.util.Locale[0];"
                + "  }"
                + "}");

    assertThat(references)
        .contains(
            "java.util.ArrayList",
            "java.io.Closeable",
            "java.util.concurrent.atomic.AtomicInteger",
            "java.io.IOException",
            "java.util.Random",
            "java.util.Date",
            "java.lang.Runnable",
            "java.lang.InterruptedException",
            "java.util.BitSet",
            "java.util.Locale");
  }

  @Test
  void findsTypesThatOnlyOccurInTheDescriptorOfAnInvokedMember() throws IOException {
    // Subject never names Passed: it only flows from one call into another.
    Set<String> references =
        referencesOf(
            "p.Subject",
            Depth.LINKAGE,
            "package p; public class Subject {"
                + "  void run(Source source, Sink sink) { sink.take(source.give()); }"
                + "}",
            "package p; public class Passed { }",
            "package p; public class Source { Passed give() { return null; } }",
            "package p; public class Sink { void take(Passed passed) { } }");

    assertThat(references).contains("p.Passed", "p.Source", "p.Sink");
  }

  @Test
  void findsAnnotationsAndGenericTypeArguments() throws IOException {
    Set<String> references =
        referencesOf(
            "p.Subject",
            Depth.LINKAGE,
            "package p; @Marker(type = java.util.Timer.class) public class Subject {"
                + "  @Deprecated java.util.List<java.util.UUID> ids;"
                + "  @SafeVarargs final <T extends java.util.Currency> void m(@Marker T... t) { }"
                + "}",
            "package p; import java.lang.annotation.*;"
                + " @Retention(RetentionPolicy.RUNTIME) public @interface Marker {"
                + "  Class<?> type() default Object.class; }");

    assertThat(references)
        .contains(
            "p.Marker",
            "java.util.Timer",
            "java.lang.Deprecated",
            "java.util.UUID",
            "java.lang.SafeVarargs",
            "java.util.Currency");
  }

  @Test
  void findsTypesOnlyNamedByLocalVariablesAtSourceDepthOnly() throws IOException {
    String subject =
        "package p; public class Subject {"
            + "  void run(java.util.function.Supplier s) {"
            + "    java.util.List<java.util.zip.CRC32> local = (java.util.List) s.get();"
            + "    System.out.println(local);"
            + "  }"
            + "}";

    assertThat(referencesOf("p.Subject", Depth.SOURCE, subject)).contains("java.util.zip.CRC32");
    assertThat(referencesOf("p.Subject", Depth.LINKAGE, subject))
        .doesNotContain("java.util.zip.CRC32")
        .contains("java.util.List", "java.util.function.Supplier");
  }

  @Test
  void keepsStringConstantsThatCanBeClassNamesAndDropsTheOthers() throws IOException {
    Set<String> references =
        referencesOf(
            "p.Subject",
            Depth.LINKAGE,
            "package p; public class Subject {"
                + "  Object a() throws Exception { return Class.forName(\"com.acme.Driver\"); }"
                + "  String b() { return \"com/acme/Internal\"; }"
                + "  String c() { return \"not a class name\"; }"
                + "  String d() { return \"SELECT * FROM t\"; }"
                + "  String e() { return \"http://example.org/a.b\"; }"
                + "}");

    assertThat(references)
        .contains("com.acme.Driver", "com.acme.Internal")
        .doesNotContain("not a class name", "SELECT * FROM t", "http://example.org/a.b");
  }

  @Test
  void supportsDynamicConstants() {
    // CONSTANT_Dynamic (tag 17) made the previous constant pool parser fail the whole analysis.
    ClassWriter writer = new ClassWriter(0);
    writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "p/Condy", null, "java/lang/Object", null);
    MethodVisitor method =
        writer.visitMethod(Opcodes.ACC_STATIC, "m", "()Ljava/lang/Object;", null, null);
    method.visitCode();
    method.visitLdcInsn(
        new ConstantDynamic(
            "value",
            "Lcom/acme/Lazy;",
            new Handle(
                Opcodes.H_INVOKESTATIC,
                "com/acme/Bootstraps",
                "make",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)"
                    + "Lcom/acme/Lazy;",
                false)));
    method.visitInsn(Opcodes.ARETURN);
    method.visitMaxs(1, 0);
    method.visitEnd();
    writer.visitEnd();

    assertThat(ClassReferenceExtractor.extract(writer.toByteArray(), Depth.LINKAGE))
        .contains("com.acme.Lazy", "com.acme.Bootstraps");
  }

  @Test
  void rejectsWhatIsNotAClassFile() {
    assertThatThrownBy(
            () -> ClassReferenceExtractor.extract(new byte[] {1, 2, 3, 4, 5, 6}, Depth.LINKAGE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ClassReferenceExtractor.extract(new byte[0], Depth.SOURCE))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
