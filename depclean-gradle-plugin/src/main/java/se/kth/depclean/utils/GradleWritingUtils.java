package se.kth.depclean.utils;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.gradle.api.artifacts.ResolvedArtifact;
import se.kth.depclean.DepCleanGradleAction;

public class GradleWritingUtils {

  /**
   * Writes the debloated-dependencies.gradle.
   *
   * @param file Target
   * @param dependenciesToAdd Direct dependencies to be written directly.
   * @param excludedTransitiveArtifactsMap Map [dependency] -> [excluded transitive child]
   * @throws IOException In case of IO issues.
   */
  public static void writeGradle(final File file, final Set<ResolvedArtifact> dependenciesToAdd,
                                 final Multimap<String, String> excludedTransitiveArtifactsMap)
          throws IOException {
    /* A multi-map [configuration] -> [dependency] */
    Multimap<String, String> configurationDependencyMap = getNewConfigurations(dependenciesToAdd);

    /* Writing starts */
    FileWriter fileWriter = new FileWriter(file, true);
    BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
    PrintWriter writer = new PrintWriter(bufferedWriter);

    writer.println("dependencies {");

    for (String configuration : configurationDependencyMap.keySet()) {
      writer.print("\t" + configuration);

      /* Getting all the dependencies with specified configuration and converting
         it to an array for ease in writing. */
      Collection<String> dependency = configurationDependencyMap.get(configuration);
      String[] dep = dependency.toArray(new String[dependency.size()]);

      /* Writing those dependencies which do not have to exclude any dependency(s).
         Simultaneously, also getting those dependencies which have to exclude
         some transitive dependencies. */
      Set<String> excludeChildrenDependencies =
              writeNonExcluded(writer, dep, excludedTransitiveArtifactsMap);

      /* Writing those dependencies which have to exclude any dependency(s). */
      if (!excludeChildrenDependencies.isEmpty()) {
        writeExcluded(writer, configuration,
                excludeChildrenDependencies, excludedTransitiveArtifactsMap);
      }
    }
    writer.println("}");
    writer.close();
  }

  // TODO: To modify later.
  /**
   * There are some dependencies configurations that are removed by Gradle above 7.0.0,
   * like runtime converted to implementation. To know more visit <a href =
   * "https://docs.gradle.org/current/userguide/upgrading_version_6.html">here.</a>, but still
   * $dependencies.getConfiguration() still returns those deprecated scopes. <br>
   * So, currently we just divide dependencies into two parts i.e. implementation
   * & testImplementation.
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
      String oldConfiguration = artifactName.split(":")[3];
      String configuration =
              oldConfiguration.startsWith("test") || oldConfiguration.endsWith("Elements")
                      ? "testImplementation" : "implementation";
      configurationDependencyMap.put(configuration, dependency);
    }
    return configurationDependencyMap;
  }

  /**
   * Writes those dependencies which don't have to exclude any transitive dependencies of their own.
   * Simultaneously, it also returns the set of dependencies which have to exclude some
   * transitive dependencies to write them separately.
   *
   * @param writer For writing.
   * @param dep Dependencies to be printed.
   * @param excludedTransitiveArtifactsMap [dependency] -> [excluded transitive dependencies].
   * @return A set of dependencies.
   */
  public static Set<String> writeNonExcluded(final PrintWriter writer,
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
  public static void writeExcluded(final PrintWriter writer,
                                   final String configuration,
                                   final Set<String> excludeChildrenDependencies,
                                   final Multimap<String, String> excludedTransitiveArtifactsMap) {
    for (String excludeDep : excludeChildrenDependencies) {
      writer.println("\t" + configuration + " ('" + excludeDep + "') {");
      Collection<String> excludeDependencies = excludedTransitiveArtifactsMap.get(excludeDep);
      excludeDependencies.forEach(s ->
              writer.println("\t\t\texclude group: '" + s.split(":")[0]
                      + "', module: '" + s.split(":")[1] + "'"));
      writer.println("\t}");
    }
  }

}
