package se.kth.depclean.core.testing;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/** Compiles and packages small Java programs, so that tests analyze real bytecode. */
public final class Bytecode {

  private Bytecode() {}

  /**
   * Compiles sources into a directory.
   *
   * @param outputDirectory where the class files are written
   * @param classpath the jars and directories to compile against
   * @param sources the compilation units, each one a complete Java source file
   * @return the output directory
   */
  public static Path compile(Path outputDirectory, List<Path> classpath, String... sources) {
    final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    final List<JavaFileObject> units = new ArrayList<>();
    for (String source : sources) {
      units.add(new SourceFile(source));
    }
    try {
      Files.createDirectories(outputDirectory);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    final List<String> options =
        new ArrayList<>(Arrays.asList("-d", outputDirectory.toString(), "-g", "-proc:none"));
    if (!classpath.isEmpty()) {
      options.add("-classpath");
      options.add(
          classpath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
    }
    if (!compiler.getTask(null, null, null, options, null, units).call()) {
      throw new IllegalStateException("The test sources do not compile");
    }
    return outputDirectory;
  }

  /**
   * Packages a directory of classes as a jar.
   *
   * @param jar the jar file to create
   * @param classesDirectory the directory to package
   * @return the jar file
   */
  public static Path jar(Path jar, Path classesDirectory) {
    try (OutputStream out = Files.newOutputStream(jar);
        JarOutputStream jarOut = new JarOutputStream(out);
        Stream<Path> files = Files.walk(classesDirectory)) {
      for (Path file : files.filter(Files::isRegularFile).sorted().collect(Collectors.toList())) {
        jarOut.putNextEntry(
            new JarEntry(classesDirectory.relativize(file).toString().replace('\\', '/')));
        jarOut.write(Files.readAllBytes(file));
        jarOut.closeEntry();
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return jar;
  }

  /**
   * Adds a text file to a directory of classes, e.g. a provider configuration file.
   *
   * @param classesDirectory the root directory
   * @param path the path of the file, relative to the root directory
   * @param content the content of the file
   */
  public static void resource(Path classesDirectory, String path, String content) {
    try {
      final Path file = classesDirectory.resolve(path);
      Files.createDirectories(file.getParent());
      Files.write(file, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static final class SourceFile extends SimpleJavaFileObject {
    private final String source;

    SourceFile(String source) {
      super(URI.create("string:///" + typeName(source).replace('.', '/') + ".java"), Kind.SOURCE);
      this.source = source;
    }

    private static String typeName(String source) {
      final java.util.regex.Matcher pkg =
          java.util.regex.Pattern.compile("package\\s+([\\w.]+)\\s*;").matcher(source);
      final java.util.regex.Matcher type =
          java.util.regex.Pattern.compile("(?:class|interface|enum|record|@interface)\\s+(\\w+)")
              .matcher(source);
      if (!type.find()) {
        throw new IllegalArgumentException("No type declared in: " + source);
      }
      return (pkg.find() ? pkg.group(1) + "." : "") + type.group(1);
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return source;
    }
  }
}
