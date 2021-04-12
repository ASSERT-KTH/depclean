package se.kth.depclean.util.json;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import fr.dutra.tools.maven.deptree.core.Node;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import org.apache.commons.io.FileUtils;
import se.kth.depclean.core.analysis.DefaultProjectDependencyAnalyzer;
import se.kth.depclean.core.analysis.graph.DefaultCallGraph;

/**
 * Custom Gson type adapter to write a JSON file with information of the dependencies.
 */
@AllArgsConstructor
public class NodeAdapter extends TypeAdapter<Node> {

  private final Set<String> usedDirectArtifactsCoordinates;
  private final Set<String> usedInheritedArtifactsCoordinates;
  private final Set<String> usedTransitiveArtifactsCoordinates;
  private final Set<String> unusedDirectArtifactsCoordinates;
  private final Set<String> unusedInheritedArtifactsCoordinates;
  private final Set<String> unusedTransitiveArtifactsCoordinates;
  private final Map<String, Long> sizeOfDependencies;
  private final DefaultProjectDependencyAnalyzer dependencyAnalyzer;
  private final File classUsageFile;
  private boolean createClassUsageCsv;

  @Override
  public void write(JsonWriter jsonWriter, Node node) throws IOException {
    String ga = node.getGroupId() + ":" + node.getArtifactId();
    String vs = node.getVersion() + ":" + node.getScope();
    String coordinates = ga + ":" + vs;
    String canonical = ga + ":" + node.getPackaging() + ":" + vs;
    String dependencyJar = node.getArtifactId() + "-" + node.getVersion() + ".jar";

    if (createClassUsageCsv) {
      writeClassUsageCsv(canonical);
    }

    JsonWriter localWriter = jsonWriter.beginObject()
        .name("id")
        .value(canonical)

        .name("coordinates")
        .value(node.getGroupId() + ":" + node.getArtifactId() + ":" + node.getVersion())

        .name("groupId")
        .value(node.getGroupId())

        .name("artifactId")
        .value(node.getArtifactId())

        .name("version")
        .value(node.getVersion())

        .name("scope")
        .value(node.getScope())

        .name("packaging")
        .value(node.getPackaging())

        .name("omitted")
        .value(node.isOmitted())

        .name("classifier")
        .value(node.getClassifier())

        .name("size")
        .value(sizeOfDependencies.get(dependencyJar))

        .name("type")
        .value((usedDirectArtifactsCoordinates.contains(coordinates) || unusedDirectArtifactsCoordinates
            .contains(coordinates)) ? "direct" :
            (usedInheritedArtifactsCoordinates.contains(coordinates) || unusedInheritedArtifactsCoordinates
                .contains(coordinates)) ? "inherited" :
                (usedTransitiveArtifactsCoordinates.contains(coordinates) || unusedTransitiveArtifactsCoordinates
                    .contains(coordinates)) ? "transitive" : "unknown")
        .name("status")
        .value((usedDirectArtifactsCoordinates.contains(coordinates) || usedInheritedArtifactsCoordinates
            .contains(coordinates) || usedTransitiveArtifactsCoordinates.contains(coordinates))
            ? "used" :
            (unusedDirectArtifactsCoordinates.contains(coordinates) || unusedInheritedArtifactsCoordinates
                .contains(coordinates) || unusedTransitiveArtifactsCoordinates.contains(coordinates))
                ? "bloated" : "unknown")

        .name("parent")
        .value(node.getParent() != null
            ? node.getParent().getArtifactCanonicalForm() : "unknown");

    JsonWriter allTypes = localWriter.name("allTypes").beginArray();
    if (dependencyAnalyzer.getArtifactClassesMap().containsKey(canonical)) {
      for (String allType : dependencyAnalyzer.getArtifactClassesMap().get(canonical).getAllTypes()) {
        allTypes.value(allType);
      }
    }
    allTypes.endArray();

    JsonWriter usedTypes = localWriter.name("usedTypes").beginArray();
    if (dependencyAnalyzer.getArtifactClassesMap().containsKey(canonical)) {
      for (String usedType : dependencyAnalyzer.getArtifactClassesMap().get(canonical).getUsedTypes()) {
        usedTypes.value(usedType);
      }
    }
    usedTypes.endArray();

    localWriter.name("usageRatio")
        .value(dependencyAnalyzer.getArtifactClassesMap().containsKey(canonical)
            ? dependencyAnalyzer.getArtifactClassesMap().get(canonical).getAllTypes().isEmpty()
            ? 0 : // handle division by zero
            ((double) dependencyAnalyzer.getArtifactClassesMap().get(canonical).getUsedTypes().size()
                / dependencyAnalyzer.getArtifactClassesMap().get(canonical).getAllTypes().size()) : -1)
        .name("children")
        .beginArray();

    for (Node c : node.getChildNodes()) {
      this.write(jsonWriter, c);
    }
    jsonWriter.endArray()
        .endObject();
  }

  private void writeClassUsageCsv(String canonical) throws IOException {
    DefaultCallGraph defaultCallGraph = new DefaultCallGraph();
    for (Map.Entry<String, Set<String>> usagePerClassMap : defaultCallGraph.getUsagesPerClass().entrySet()) {
      String key = usagePerClassMap.getKey();
      Set<String> value = usagePerClassMap.getValue();
      for (String s : value) {
        if (dependencyAnalyzer.getArtifactClassesMap().containsKey(canonical) && dependencyAnalyzer
            .getArtifactClassesMap().get(canonical).getAllTypes().contains(s)) {
          String triplet = key + "," + s + "," + canonical + "\n";
          FileUtils.write(classUsageFile, triplet, Charset.defaultCharset(), true);
        }
      }
    }
  }

  @Override
  public Node read(JsonReader jsonReader) {
    throw new UnsupportedOperationException();
  }
}
