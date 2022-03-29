package se.kth.depclean.utils.json;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ResolvedDependency;
import org.jetbrains.annotations.NotNull;
import com.google.gson.stream.JsonWriter;
import lombok.extern.slf4j.Slf4j;
import se.kth.depclean.analysis.DefaultGradleProjectDependencyAnalyzer;
import se.kth.depclean.core.analysis.graph.DefaultCallGraph;
import se.kth.depclean.core.model.ClassName;

/**
 * Uses the DepClean analysis results and the declared dependencies of the project
 * to produce a JSON file. This file represent the structure of the dependency
 * tree enriched with metadata of the usage or not of each dependency.
 */
@Slf4j
public class writeJsonResult {

  private final Project project;
  private final Set<ResolvedDependency> allDependencies;
  private final Map<String, Long> sizeOfDependencies;
  private final DefaultGradleProjectDependencyAnalyzer dependencyAnalyzer;
  private final Set<String> usedDirectArtifactsCoordinates;
  private final Set<String> usedInheritedArtifactsCoordinates;
  private final Set<String> usedTransitiveArtifactsCoordinates;
  private final Set<String> unusedDirectArtifactsCoordinates;
  private final Set<String> unusedInheritedArtifactsCoordinates;
  private final Set<String> unusedTransitiveArtifactsCoordinates;
  private final File classUsageFile;
  private final boolean createClassUsageCsv;

  /**
   * Ctor.
   */
  public writeJsonResult(Project project,
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
    this.dependencyAnalyzer = dependencyAnalyzer;
    this.sizeOfDependencies = sizeOfDependencies;
    this.createClassUsageCsv = createClassUsageCsv;
    this.usedDirectArtifactsCoordinates = usedDirectArtifactsCoordinates;
    this.usedInheritedArtifactsCoordinates = usedInheritedArtifactsCoordinates;
    this.usedTransitiveArtifactsCoordinates = usedTransitiveArtifactsCoordinates;
    this.unusedDirectArtifactsCoordinates = unusedDirectArtifactsCoordinates;
    this.unusedInheritedArtifactsCoordinates = unusedInheritedArtifactsCoordinates;
    this.unusedTransitiveArtifactsCoordinates = unusedTransitiveArtifactsCoordinates;
  }

  /**
   * Write the result.json file of debloated result.
   *
   * @param fw File to be generated.
   * @throws IOException If the JSON file cannot be written.
   */
  public void write(FileWriter fw) throws IOException {

    BufferedWriter bw = new BufferedWriter(fw, 512000);
    JsonWriter jsonWriter = new JsonWriter(bw);

    /*  First adding the project artifact as the json parent. */
    String projectGroupId = project.getGroup().toString();
    String projectArtifactId = project.getName();
    String projectVersion = project.getVersion().toString();
    String projectId = projectGroupId + ":" + projectArtifactId + ":" + projectVersion;
    String projectCoordinates = projectId + ":" + null;
    String projectJar = projectArtifactId + "-" + projectVersion + ".jar";

    if (createClassUsageCsv) {
      writeClassUsageCsv(projectId);
    }

    jsonWriter.setIndent("  ");
    JsonWriter localWriter = jsonWriter.beginObject()

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

  private void writeChild(JsonWriter jsonWriter, Set<ResolvedDependency> allDependencies) throws IOException {
    for (ResolvedDependency dependency : allDependencies) {
      String dependencyId = dependency.getName();
      String configuration = dependency.getConfiguration();
      String coordinates = dependencyId + ":" + configuration;
      String groupId = dependency.getModuleGroup();
      String artifactId = coordinates.split(":")[1];
      String version = dependency.getModuleVersion();
      String dependencyJar = artifactId + "-" + version + ".jar";

      if (createClassUsageCsv) {
        writeClassUsageCsv(dependencyId);
      }

        JsonWriter childWriter = jsonWriter.beginObject()

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
        jsonWriter.endArray()
                .endObject();
    }
  }

  private void writeParent(ResolvedDependency dependency, JsonWriter childWriter) throws IOException {
    JsonWriter localWriter = childWriter.name("parent(s)").beginArray();
    if (!dependency.getParents().isEmpty()) {
      for (ResolvedDependency parent : dependency.getParents()) {
        localWriter.value(parent.toString());
      }
    }
    localWriter.endArray();
  }

  @NotNull
  private String getStatus(String coordinates) {
    return (usedDirectArtifactsCoordinates.contains(coordinates) || usedInheritedArtifactsCoordinates
        .contains(coordinates) || usedTransitiveArtifactsCoordinates.contains(coordinates))
        ? "used" :
        (unusedDirectArtifactsCoordinates.contains(coordinates) || unusedInheritedArtifactsCoordinates
            .contains(coordinates) || unusedTransitiveArtifactsCoordinates.contains(coordinates))
            ? "bloated" : "unknown";
  }

  @NotNull
  private String getType(String coordinates) {
    return (usedDirectArtifactsCoordinates.contains(coordinates) || unusedDirectArtifactsCoordinates
        .contains(coordinates)) ? "direct" :
        (usedInheritedArtifactsCoordinates.contains(coordinates) || unusedInheritedArtifactsCoordinates
            .contains(coordinates)) ? "inherited" :
            (usedTransitiveArtifactsCoordinates.contains(coordinates) || unusedTransitiveArtifactsCoordinates
                .contains(coordinates)) ? "transitive" : "unknown";
  }

  private void writeUsageRatio(String dependencyId, JsonWriter localWriter) throws IOException {
    localWriter.name("usageRatio")
        .value(dependencyAnalyzer.getDependenciesClassesMap().containsKey(dependencyId)
            ? dependencyAnalyzer.getDependenciesClassesMap().get(dependencyId).getAllTypes().isEmpty()
            ? 0 : // handle division by zero
            ((double) dependencyAnalyzer.getDependenciesClassesMap().get(dependencyId).getUsedTypes().size()
                / dependencyAnalyzer.getDependenciesClassesMap().get(dependencyId).getAllTypes().size()) : -1)
        .name("children(s)")
        .beginArray();
  }

  private void writeUsedTypes(String dependencyId, JsonWriter localWriter) throws IOException {
    JsonWriter usedTypes = localWriter.name("usedTypes").beginArray();
    if (dependencyAnalyzer.getDependenciesClassesMap().containsKey(dependencyId)) {
      for (ClassName usedType : dependencyAnalyzer.getDependenciesClassesMap().get(dependencyId).getUsedTypes()) {
        System.out.println("Used type: " + usedType.toString());
        usedTypes.value(usedType.getValue());
      }
    }
    usedTypes.endArray();
  }

  private void writeAllTypes(String dependencyId, JsonWriter localWriter) throws IOException {
    JsonWriter allTypes = localWriter.name("allTypes").beginArray();
    if (dependencyAnalyzer.getDependenciesClassesMap().containsKey(dependencyId)) {
      for (ClassName allType : dependencyAnalyzer.getDependenciesClassesMap().get(dependencyId).getAllTypes()) {
        allTypes.value(allType.getValue());
      }
    }
    allTypes.endArray();
  }

  private void writeClassUsageCsv(String dependencyId) throws IOException {
    DefaultCallGraph defaultCallGraph = new DefaultCallGraph();
    for (Map.Entry<String, Set<String>> usagePerClassMap : defaultCallGraph.getUsagesPerClass().entrySet()) {
      String key = usagePerClassMap.getKey();
      Set<String> value = usagePerClassMap.getValue();
      for (String s : value) {
        if (dependencyAnalyzer.getDependenciesClassesMap().containsKey(dependencyId) && dependencyAnalyzer
            .getDependenciesClassesMap().get(dependencyId).getAllTypes().contains(s)) {
          String triplet = key + "," + s + "," + dependencyId + "\n";
          FileUtils.write(classUsageFile, triplet, Charset.defaultCharset(), true);
        }
      }
    }
  }
}
