package se.kth.depclean.core.analysis.asm;

import java.util.Set;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.signature.SignatureVisitor;

/**
 * Extracts the names of all the classes that a class file references.
 *
 * <p>The class file is parsed exactly once, with ASM's {@link ClassReader}. The references come
 * from two places:
 *
 * <ul>
 *   <li>the constant pool, read through the reader's own index: every class the bytecode links
 *       against (instantiations, casts, member owners, catch types, class literals) has a {@code
 *       CONSTANT_Class} entry, and every invoked method or accessed field carries its parameter,
 *       return and field types in a descriptor. String constants are kept when they look like a
 *       class name, which is how reflective lookups such as {@code Class.forName("com.foo.Bar")}
 *       are detected;
 *   <li>the class structure, which holds what is not in the constant pool as a class entry:
 *       annotations, generic signatures and the descriptors of the declared members.
 * </ul>
 */
public final class ClassReferenceExtractor {

  /** How much of a class file is inspected. */
  public enum Depth {
    /**
     * Everything, including the debug information of method bodies. A type that is only named by a
     * local variable (e.g. {@code List<Foo> foos = bar.list()}) is needed to compile the class, so
     * this is the depth for the classes of the analyzed project.
     */
    SOURCE,
    /**
     * Only what the JVM needs to link and run the class. Method bodies are not decoded: all their
     * references are in the constant pool already. This is the depth for third-party classes, which
     * are never recompiled.
     */
    LINKAGE
  }

  // Constant pool tags, see JVMS 4.4
  private static final int CONSTANT_CLASS = 7;
  private static final int CONSTANT_STRING = 8;
  private static final int CONSTANT_NAME_AND_TYPE = 12;
  private static final int CONSTANT_METHOD_TYPE = 16;

  /** Class names longer than this cannot exist on any file system DepClean runs on. */
  private static final int MAX_CLASS_NAME_LENGTH = 1024;

  private ClassReferenceExtractor() {}

  /**
   * Extracts the classes referenced by a class file.
   *
   * @param classFile the content of the class file
   * @param depth how much of the class file to inspect
   * @return the fully qualified, dot-separated names of the referenced classes
   * @throws IllegalArgumentException if the class file is malformed, or of a version ASM does not
   *     support
   */
  public static Set<String> extract(byte[] classFile, Depth depth) {
    final ResultCollector collector = new ResultCollector();
    try {
      final ClassReader reader = new ClassReader(classFile);
      collectConstantPool(reader, collector);
      final AnnotationVisitor annotationVisitor = new DefaultAnnotationVisitor(collector);
      final SignatureVisitor signatureVisitor = new DefaultSignatureVisitor(collector);
      final FieldVisitor fieldVisitor = new DefaultFieldVisitor(annotationVisitor, collector);
      final MethodVisitor methodVisitor =
          new DefaultMethodVisitor(annotationVisitor, signatureVisitor, collector);
      // Stack map frames are never visited, so decoding them is always skipped.
      final int flags =
          depth == Depth.SOURCE
              ? ClassReader.SKIP_FRAMES
              : ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG;
      reader.accept(
          new DefaultClassVisitor(
              signatureVisitor, annotationVisitor, fieldVisitor, methodVisitor, collector),
          flags);
    } catch (IndexOutOfBoundsException | NegativeArraySizeException e) {
      // ASM does not validate its input: a truncated or corrupted class file fails this way.
      throw new IllegalArgumentException("Malformed class file", e);
    }
    return collector.getDependencies();
  }

  private static void collectConstantPool(ClassReader reader, ResultCollector collector) {
    final char[] buffer = new char[reader.getMaxStringLength()];
    final int itemCount = reader.getItemCount();
    for (int item = 1; item < itemCount; item++) {
      final int offset = reader.getItem(item);
      // The slot after a long or a double constant is unused and has no offset.
      if (offset == 0) {
        continue;
      }
      switch (reader.readByte(offset - 1)) {
        case CONSTANT_CLASS:
          collector.addName(reader.readUTF8(offset, buffer));
          break;
        case CONSTANT_STRING:
          addIfClassName(reader.readUTF8(offset, buffer), collector);
          break;
        case CONSTANT_NAME_AND_TYPE:
          // name_index, then descriptor_index: the types of an invoked method or accessed field
          collector.addDesc(reader.readUTF8(offset + 2, buffer));
          break;
        case CONSTANT_METHOD_TYPE:
          collector.addDesc(reader.readUTF8(offset, buffer));
          break;
        default:
          // the other constants do not name a class
          break;
      }
    }
  }

  /**
   * Keeps a string constant if it can be the name of a class, in either the source ({@code
   * com.foo.Bar}) or the internal ({@code com/foo/Bar}) form. Anything else, i.e. nearly every
   * string of a program, is dropped here instead of being carried through the whole analysis.
   */
  private static void addIfClassName(String value, ResultCollector collector) {
    if (value == null || value.isEmpty() || value.length() > MAX_CLASS_NAME_LENGTH) {
      return;
    }
    String name = value;
    if (name.startsWith("[L") && name.endsWith(";")) {
      name = name.substring(2, name.length() - 1);
    }
    if (name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0))) {
      return;
    }
    for (int i = 1; i < name.length(); i++) {
      char c = name.charAt(i);
      if (c != '.' && c != '/' && !Character.isJavaIdentifierPart(c)) {
        return;
      }
    }
    collector.addName(name);
  }
}
