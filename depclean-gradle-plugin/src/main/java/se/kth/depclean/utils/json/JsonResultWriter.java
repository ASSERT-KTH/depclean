package se.kth.depclean.utils.json;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.gson.stream.JsonWriter;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ResolvedDependency;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import se.kth.depclean.analysis.DefaultGradleProjectDependencyAnalyzer;
import se.kth.depclean.core.analysis.DependencyTypes;
import se.kth.depclean.core.analysis.graph.DefaultCallGraph;
import se.kth.depclean.core.model.ClassName;

/**
 * Uses the DepClean analysis results and the declared dependencies of the project to produce a JSON
 * file. This file represent the structure of the dependency tree enriched with metadata of the
 * usage or not of each dependency.
 */
public class JsonResultWriter {

  private final Project project;
  private final Set<ResolvedDependency> allDependencies;
  private final Map<String, Long> sizeOfDependencies;
  private final Set<String> usedDirectArtifactsCoordinates;
  private final Set<String> usedInheritedArtifactsCoordinates;
  private final Set<String> usedTransitiveArtifactsCoordinates;
  private final Set<String> unusedDirectArtifactsCoordinates;
  private final Set<String> unusedInheritedArtifactsCoordinates;
  private final Set<String> unusedTransitiveArtifactsCoordinates;
  private final File classUsageFile;
  private final boolean createClassUsageCsv;
  private final Map<String, DependencyTypes> dependenciesClassesMap;
  private final Set<String> classUsageDependenciesWritten = new HashSet<>();
  @Nullable private Writer classUsageWriter;
  @Nullable private Map<String, Set<String>> originsByTarget;

  /** Ctor. */
  public JsonResultWriter(
      Project project,
      File classUsageFile,
      DefaultGradleProjectDependencyAnalyzer dependencyAnalyzer,
      Map<String, Long> sizeOfDependencies,
      boolean createClassUsageCsv,
      Set<ResolvedDependency> declaredDependencies,
      Set<String> usedDirectArtifactsCoordinates,
      Set<String> usedInheritedArtifactsCoordinates,
      Set<String> usedTransitiveArtifactsCoordinates,
      Set<String> unusedDirectArtifactsCoordinates,
      Set<String> unusedInheritedArtifactsCoordinates,
      Set<String> unusedTransitiveArtifactsCoordinates) {
    this.project = project;
    this.classUsageFile = classUsageFile;
    this.allDependencies = declaredDependencies;
    this.sizeOfDependencies = sizeOfDependencies;
    this.createClassUsageCsv = createClassUsageCsv;
    this.usedDirectArtifactsCoordinates = usedDirectArtifactsCoordinates;
    this.usedInheritedArtifactsCoordinates = usedInheritedArtifactsCoordinates;
    this.usedTransitiveArtifactsCoordinates = usedTransitiveArtifactsCoordinates;
    this.unusedDirectArtifactsCoordinates = unusedDirectArtifactsCoordinates;
    this.unusedInheritedArtifactsCoordinates = unusedInheritedArtifactsCoordinates;
    this.unusedTransitiveArtifactsCoordinates = unusedTransitiveArtifactsCoordinates;
    this.dependenciesClassesMap = dependencyAnalyzer.getDependenciesClassesMap();
  }

  /**
   * Write the result.json file of debloated result.
   *
   * @param fw File to be generated.
   * @throws IOException If the JSON file cannot be written.
   */
  public void write(FileWriter fw) throws IOException {
    // Appends below the header that DepCleanGradleAction writes when it (re)creates the file.
    try (Writer csv =
        createClassUsageCsv
            ? Files.newBufferedWriter(
                classUsageFile.toPath(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)
            : null) {
      classUsageWriter = csv;
      writeTree(fw);
    } finally {
      classUsageWriter = null;
    }
  }

  private void writeTree(FileWriter fw) throws IOException {

    BufferedWriter bw = new BufferedWriter(fw, 512000);
    JsonWriter jsonWriter = new JsonWriter(bw);

    /* First adding the project artifact as the json parent. */
    String projectGroupId = project.getGroup().toString();
    String projectArtifactId = project.getName();
    String projectVersion = project.getVersion().toString();
    String projectId = projectGroupId + ":" + projectArtifactId + ":" + projectVersion;
    String projectCoordinates = projectId + ":" + null;
    String projectJar = projectArtifactId + "-" + projectVersion + ".jar";

    writeClassUsageCsv(projectId);

    jsonWriter.setIndent("  ");
    JsonWriter localWriter =
        jsonWriter
            .beginObject()
            .name("coordinates")
            .value(projectCoordinates)
            .name("id")
            .value(projectId)
            .name("groupId")
            .value(projectGroupId)
            .name("artifactId")
            .value(projectArtifactId)
            .name("version")
            .value(projectVersion)
            .name("size")
            .value(sizeOfDependencies.get(projectJar))
            .name("type")
            .value(getType(projectCoordinates))
            .name("status")
            .value(getStatus(projectCoordinates));

    writeAllTypes(projectId, localWriter);
    writeUsedTypes(projectId, localWriter);
    writeUsageRatio(projectId, localWriter);

    /* Now writing the project's dependencies as children of project. */
    writeChild(jsonWriter, allDependencies);
    jsonWriter.endArray().endObject();
    bw.flush();
    bw.close();
  }

  private void writeChild(JsonWriter jsonWriter, Set<ResolvedDependency> allDependencies)
      throws IOException {
    for (ResolvedDependency dependency : allDependencies) {
      String dependencyId = dependency.getName();
      String configuration = dependency.getConfiguration();
      String coordinates = dependencyId + ":" + configuration;
      String groupId = dependency.getModuleGroup();
      String artifactId = Iterables.get(Splitter.on(':').split(coordinates), 1);
      String version = dependency.getModuleVersion();
      String dependencyJar = artifactId + "-" + version + ".jar";

      writeClassUsageCsv(dependencyId);

      JsonWriter childWriter =
          jsonWriter
              .beginObject()
              .name("coordinates")
              .value(coordinates)
              .name("id")
              .value(dependencyId)
              .name("groupId")
              .value(groupId)
              .name("artifactId")
              .value(artifactId)
              .name("version")
              .value(version)
              .name("configuration")
              .value(configuration)
              .name("size")
              .value(sizeOfDependencies.get(dependencyJar))
              .name("type")
              .value(getType(coordinates))
              .name("status")
              .value(getStatus(coordinates));

      writeParent(dependency, childWriter);
      writeAllTypes(dependencyId, childWriter);
      writeUsedTypes(dependencyId, childWriter);
      writeUsageRatio(dependencyId, childWriter);

      if (!dependency.getChildren().isEmpty()) {
        this.writeChild(childWriter, dependency.getChildren());
      }
      jsonWriter.endArray().endObject();
    }
  }

  private void writeParent(ResolvedDependency dependency, JsonWriter childWriter)
      throws IOException {
    JsonWriter localWriter = childWriter.name("parent(s)").beginArray();
    if (!dependency.getParents().isEmpty()) {
      for (ResolvedDependency parent : dependency.getParents()) {
        localWriter.value(parent.toString());
      }
    }
    localWriter.endArray();
  }

  @NonNull
  private String getStatus(String coordinates) {
    return (usedDirectArtifactsCoordinates.contains(coordinates)
            || usedInheritedArtifactsCoordinates.contains(coordinates)
            || usedTransitiveArtifactsCoordinates.contains(coordinates))
        ? "used"
        : (unusedDirectArtifactsCoordinates.contains(coordinates)
                || unusedInheritedArtifactsCoordinates.contains(coordinates)
                || unusedTransitiveArtifactsCoordinates.contains(coordinates))
            ? "bloated"
            : "unknown";
  }

  @NonNull
  private String getType(String coordinates) {
    return (usedDirectArtifactsCoordinates.contains(coordinates)
            || unusedDirectArtifactsCoordinates.contains(coordinates))
        ? "direct"
        : (usedInheritedArtifactsCoordinates.contains(coordinates)
                || unusedInheritedArtifactsCoordinates.contains(coordinates))
            ? "inherited"
            : (usedTransitiveArtifactsCoordinates.contains(coordinates)
                    || unusedTransitiveArtifactsCoordinates.contains(coordinates))
                ? "transitive"
                : "unknown";
  }

  private void writeUsageRatio(String dependencyId, JsonWriter localWriter) throws IOException {
    DependencyTypes types = dependenciesClassesMap.get(dependencyId);
    localWriter
        .name("usageRatio")
        .value(
            types == null
                ? -1
                : types.getAllTypes().isEmpty()
                    ? 0 // handle division by zero
                    : ((double) types.getUsedTypes().size() / types.getAllTypes().size()))
        .name("children(s)")
        .beginArray();
  }

  private void writeUsedTypes(String dependencyId, JsonWriter localWriter) throws IOException {
    JsonWriter usedTypes = localWriter.name("usedTypes").beginArray();
    DependencyTypes types = dependenciesClassesMap.get(dependencyId);
    if (types != null) {
      for (ClassName usedType : types.getUsedTypes()) {
        usedTypes.value(usedType.getValue());
      }
    }
    usedTypes.endArray();
  }

  private void writeAllTypes(String dependencyId, JsonWriter localWriter) throws IOException {
    JsonWriter allTypes = localWriter.name("allTypes").beginArray();
    DependencyTypes types = dependenciesClassesMap.get(dependencyId);
    if (types != null) {
      for (ClassName allType : types.getAllTypes()) {
        allTypes.value(allType.getValue());
      }
    }
    allTypes.endArray();
  }

  /**
   * Writes one {@code caller,callee,dependency} line per call-graph edge whose target class belongs
   * to the given dependency, sorted by caller then callee. Each dependency is written once even if
   * it appears several times in the dependency tree.
   */
  private void writeClassUsageCsv(String dependencyId) throws IOException {
    DependencyTypes types = dependenciesClassesMap.get(dependencyId);
    if (classUsageWriter == null
        || types == null
        || !classUsageDependenciesWritten.add(dependencyId)) {
      return;
    }
    Map<String, Set<String>> originsByTarget = originsByTarget();
    Map<String, Set<String>> targetsByOrigin = new TreeMap<>();
    for (ClassName type : types.getAllTypes()) {
      for (String origin : originsByTarget.getOrDefault(type.getValue(), new HashSet<>())) {
        targetsByOrigin.computeIfAbsent(origin, k -> new TreeSet<>()).add(type.getValue());
      }
    }
    for (Map.Entry<String, Set<String>> edges : targetsByOrigin.entrySet()) {
      for (String target : edges.getValue()) {
        classUsageWriter.write(edges.getKey() + "," + target + "," + dependencyId + "\n");
      }
    }
  }

  /** The call graph inverted (target class to its callers), built once per JSON write. */
  private Map<String, Set<String>> originsByTarget() {
    if (originsByTarget == null) {
      Map<String, Set<String>> inverted = new HashMap<>();
      for (Map.Entry<String, Set<String>> usages :
          DefaultCallGraph.getUsagesPerClass().entrySet()) {
        for (String target : usages.getValue()) {
          inverted.computeIfAbsent(target, k -> new HashSet<>()).add(usages.getKey());
        }
      }
      originsByTarget = inverted;
    }
    return originsByTarget;
  }
}
