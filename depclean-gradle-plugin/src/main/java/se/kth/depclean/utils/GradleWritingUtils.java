package se.kth.depclean.utils;

import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.gradle.api.artifacts.ResolvedArtifact;
import se.kth.depclean.DepCleanGradleAction;

public class GradleWritingUtils {

  private GradleWritingUtils() {}

  /**
   * Writes the debloated-dependencies.gradle.
   *
   * @param file Target
   * @param dependenciesToAdd Direct dependencies to be written directly.
   * @param excludedTransitiveArtifactsMap Map [dependency] -> [excluded transitive child]
   * @throws IOException In case of IO issues.
   */
  public static void writeGradle(
      final File file,
      final Set<ResolvedArtifact> dependenciesToAdd,
      final Multimap<String, String> excludedTransitiveArtifactsMap)
      throws IOException {
    /* A multi-map [configuration] -> [dependency] */
    Multimap<String, String> configurationDependencyMap = getNewConfigurations(dependenciesToAdd);

    /* Writing starts */
    FileWriter fileWriter = new FileWriter(file, StandardCharsets.UTF_8, true);
    BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
    PrintWriter writer = new PrintWriter(bufferedWriter);

    writer.println("dependencies {");

    for (String configuration : configurationDependencyMap.keySet()) {
      writer.print("\t" + configuration);

      /*
       * Getting all the dependencies with specified configuration and converting
       * it to an array for ease in writing.
       */
      Collection<String> dependency = configurationDependencyMap.get(configuration);
      String[] dep = dependency.toArray(new String[dependency.size()]);

      /*
       * Writing those dependencies which do not have to exclude any dependency(s).
       * Simultaneously, also getting those dependencies which have to exclude
       * some transitive dependencies.
       */
      Set<String> excludeChildrenDependencies =
          writeNonExcluded(writer, dep, excludedTransitiveArtifactsMap);

      /* Writing those dependencies which have to exclude any dependency(s). */
      if (!excludeChildrenDependencies.isEmpty()) {
        writeExcluded(
            writer, configuration, excludeChildrenDependencies, excludedTransitiveArtifactsMap);
      }
    }
    writer.println("}");
    writer.close();
  }

  /** The configuration a production dependency is written back into. */
  private static final String IMPLEMENTATION = "implementation";

  /**
   * Mapping from the deprecated configurations that Gradle removed in 7.0.0 to their modern
   * equivalents. To know more visit <a href =
   * "https://docs.gradle.org/current/userguide/upgrading_version_6.html">here.</a>
   */
  private static final ImmutableMap<String, String> LEGACY_CONFIGURATION_MAPPING =
      ImmutableMap.<String, String>builder()
          .put("compile", IMPLEMENTATION)
          .put("default", IMPLEMENTATION)
          .put("runtime", "runtimeOnly")
          .put("testCompile", "testImplementation")
          .put("testRuntime", "testRuntimeOnly")
          .build();

  /**
   * Translates a configuration reported for a resolved artifact into the configuration that should
   * be written back into the debloated build file.
   *
   * @param legacyConfiguration Configuration of the artifact, possibly a deprecated one.
   * @return The configuration to write, for example {@code implementation} or {@code
   *     testImplementation}.
   */
  private static String toModernConfiguration(@Nullable final String legacyConfiguration) {
    if (legacyConfiguration == null) {
      return IMPLEMENTATION;
    }
    String mapped = LEGACY_CONFIGURATION_MAPPING.get(legacyConfiguration);
    if (mapped != null) {
      return mapped;
    }
    /*
     * Everything else is either already modern (implementation, runtimeOnly, api, ...) or one of
     * Gradle's internal variant names such as apiElements/runtimeElements. Those variants only
     * belong to the test source set when they carry the test prefix, so the trailing "Elements"
     * must not be read as "test".
     */
    return legacyConfiguration.startsWith("test") ? "testImplementation" : IMPLEMENTATION;
  }

  /**
   * Splits the dependencies to add over the configurations they should be declared in.
   *
   * @param dependenciesToAdd All dependencies to be added.
   * @return A multi-map with value as a dependency and key as it's configuration.
   */
  public static Multimap<String, String> getNewConfigurations(
      final Set<ResolvedArtifact> dependenciesToAdd) {
    Multimap<String, String> configurationDependencyMap = ArrayListMultimap.create();
    for (ResolvedArtifact artifact : dependenciesToAdd) {
      String artifactName = DepCleanGradleAction.getName(artifact);
      String dependency = DepCleanGradleAction.getArtifactGroupArtifactId(artifactName);
      String oldConfiguration = Iterables.get(Splitter.on(':').split(artifactName), 3);
      configurationDependencyMap.put(toModernConfiguration(oldConfiguration), dependency);
    }
    return configurationDependencyMap;
  }

  /**
   * Writes those dependencies which don't have to exclude any transitive dependencies of their own.
   * Simultaneously, it also returns the set of dependencies which have to exclude some transitive
   * dependencies to write them separately.
   *
   * @param writer For writing.
   * @param dep Dependencies to be printed.
   * @param excludedTransitiveArtifactsMap [dependency] -> [excluded transitive dependencies].
   * @return A set of dependencies.
   */
  public static Set<String> writeNonExcluded(
      final PrintWriter writer,
      final String[] dep,
      final Multimap<String, String> excludedTransitiveArtifactsMap) {
    Set<String> excludeChildrenDependencies = new HashSet<>();
    int size = dep.length - 1;
    for (int i = 0; i < size; i++) {
      if (excludedTransitiveArtifactsMap.containsKey(dep[i])) {
        excludeChildrenDependencies.add(dep[i]);
      } else {
        writer.println("\t\t\t'" + dep[i] + "',");
      }
    }
    writer.println("\t\t\t'" + dep[size] + "'\n");
    return excludeChildrenDependencies;
  }

  /**
   * Writes those dependencies which have to exclude some of their transitive dependency(s).
   *
   * @param writer For writing.
   * @param configuration Corresponding configuration.
   * @param excludeChildrenDependencies Transitive dependencies to be excluded.
   * @param excludedTransitiveArtifactsMap [dependency] -> [excluded transitive dependencies].
   */
  public static void writeExcluded(
      final PrintWriter writer,
      final String configuration,
      final Set<String> excludeChildrenDependencies,
      final Multimap<String, String> excludedTransitiveArtifactsMap) {
    for (String excludeDep : excludeChildrenDependencies) {
      writer.println("\t" + configuration + " ('" + excludeDep + "') {");
      Collection<String> excludeDependencies = excludedTransitiveArtifactsMap.get(excludeDep);
      excludeDependencies.forEach(
          s -> {
            List<String> parts = Splitter.on(':').splitToList(s);
            writer.println(
                "\t\t\texclude group: '" + parts.get(0) + "', module: '" + parts.get(1) + "'");
          });
      writer.println("\t}");
    }
  }
}
