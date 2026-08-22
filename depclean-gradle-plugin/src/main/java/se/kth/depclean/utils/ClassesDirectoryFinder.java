package se.kth.depclean.utils;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

/**
 * Resolves the directories containing the compiled classes of a Gradle project.
 *
 * <p>Instead of assuming the conventional JVM layout ({@code build/classes/java/main}), this finder
 * resolves the output directories registered in the project's {@link SourceSetContainer} (covering
 * custom source sets and languages), scans the conventional {@code build/classes} layout for any
 * language and source set, and additionally supports Android layouts such as {@code
 * build/intermediates/javac/[variant]/classes} and {@code build/tmp/kotlin-classes/[variant]}
 * (multi-flavor variants included).
 */
public final class ClassesDirectoryFinder {

  private ClassesDirectoryFinder() {}

  /**
   * Finds all existing directories with compiled classes of the given project.
   *
   * @param project The Gradle project.
   * @param ignoreTests If true, test source sets and test variants are excluded.
   * @return The existing class output directories, possibly empty.
   */
  public static Set<File> findClassesDirectories(final Project project, final boolean ignoreTests) {
    Set<File> result = new LinkedHashSet<>();
    addSourceSetOutputs(project, ignoreTests, result);
    File buildDir = new File(project.getProjectDir(), "build");
    addConventionalLayout(new File(buildDir, "classes"), ignoreTests, result);
    addVariantLayout(new File(new File(buildDir, "intermediates"), "javac"), ignoreTests, result);
    addVariantLayout(new File(new File(buildDir, "tmp"), "kotlin-classes"), ignoreTests, result);
    return result;
  }

  /** Collects the class output directories registered in the project's source sets. */
  private static void addSourceSetOutputs(
      final Project project, final boolean ignoreTests, final Set<File> result) {
    SourceSetContainer sourceSets = project.getExtensions().findByType(SourceSetContainer.class);
    if (sourceSets == null) {
      return;
    }
    for (SourceSet sourceSet : sourceSets) {
      if (ignoreTests && isTestName(sourceSet.getName())) {
        continue;
      }
      for (File dir : sourceSet.getOutput().getClassesDirs().getFiles()) {
        if (dir.isDirectory()) {
          result.add(dir);
        }
      }
    }
  }

  /** Scans the conventional {@code build/classes/<language>/<sourceSet>} layout. */
  private static void addConventionalLayout(
      final File classesDir, final boolean ignoreTests, final Set<File> result) {
    File[] languages = classesDir.listFiles(File::isDirectory);
    if (languages == null) {
      return;
    }
    for (File language : languages) {
      File[] sourceSetDirs = language.listFiles(File::isDirectory);
      if (sourceSetDirs == null) {
        continue;
      }
      for (File sourceSetDir : sourceSetDirs) {
        if (ignoreTests && isTestName(sourceSetDir.getName())) {
          continue;
        }
        result.add(sourceSetDir);
      }
    }
  }

  /**
   * Scans Android per-variant layouts. For {@code intermediates/javac} the classes live in a {@code
   * classes} subdirectory of each variant; for {@code tmp/kotlin-classes} the variant directory
   * itself contains the classes.
   */
  private static void addVariantLayout(
      final File variantsDir, final boolean ignoreTests, final Set<File> result) {
    File[] variants = variantsDir.listFiles(File::isDirectory);
    if (variants == null) {
      return;
    }
    for (File variant : variants) {
      if (ignoreTests && isTestName(variant.getName())) {
        continue;
      }
      File classesSubDir = new File(variant, "classes");
      result.add(classesSubDir.isDirectory() ? classesSubDir : variant);
    }
  }

  private static boolean isTestName(final String name) {
    return name.toLowerCase(Locale.ROOT).contains("test");
  }
}
