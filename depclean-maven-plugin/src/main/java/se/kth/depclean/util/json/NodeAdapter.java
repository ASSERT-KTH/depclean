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
import se.kth.depclean.core.analysis.graph.DefaultCallGraph;
import se.kth.depclean.core.analysis.model.DependencyAnalysisInfo;
import se.kth.depclean.core.analysis.model.ProjectDependencyAnalysis;

/**
 * Custom Gson type adapter to write a JSON file with information of the dependencies.
 */
@AllArgsConstructor
public class NodeAdapter extends TypeAdapter<Node> {

  private final ProjectDependencyAnalysis analysis;
  private final File classUsageFile;
  private final boolean createClassUsageCsv;

  @Override
  public void write(JsonWriter jsonWriter, Node node) throws IOException {
    String ga = node.getGroupId() + ":" + node.getArtifactId();
    String gav = ga + ":" + node.getVersion();
    String vs = node.getVersion() + ":" + node.getScope();
    String canonical = ga + ":" + node.getPackaging() + ":" + vs;
    String dependencyJar = node.getArtifactId() + "-" + node.getVersion() + ".jar";

    if (createClassUsageCsv) {
      writeClassUsageCsv(canonical);
    }

    final DependencyAnalysisInfo dependencyInfo = analysis.getDependencyInfo(gav);

    JsonWriter localWriter = jsonWriter.beginObject()
        .name("id")
        .value(canonical)

        .name("coordinates")
        .value(gav)

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
        .value(dependencyInfo.getSize())

        .name("type")
        .value(dependencyInfo.getType())

        .name("status")
        .value(dependencyInfo.getStatus())

        .name("parent")
        .value(getParent(node));

    writeAllTypes(dependencyInfo, localWriter);
    writeUsedTypes(dependencyInfo, localWriter);
    writeUsageRatio(dependencyInfo, localWriter);

    for (Node c : node.getChildNodes()) {
      this.write(jsonWriter, c);
    }
    jsonWriter.endArray()
        .endObject();
  }

  private String getParent(Node node) {
    return node.getParent() != null ? node.getParent().getArtifactCanonicalForm() : "unknown";
  }

  private void writeUsageRatio(DependencyAnalysisInfo info, JsonWriter localWriter) throws IOException {
    localWriter.name("usageRatio")
        .value(info.getAllTypes().isEmpty() ? 0 : ((double) info.getUsedTypes().size() / info.getAllTypes().size()))
        .name("children")
        .beginArray();
  }

  private void writeUsedTypes(DependencyAnalysisInfo info, JsonWriter localWriter) throws IOException {
    JsonWriter usedTypes = localWriter.name("usedTypes").beginArray();
    for (String usedType : info.getUsedTypes()) {
      usedTypes.value(usedType);
    }
    usedTypes.endArray();
  }

  private void writeAllTypes(DependencyAnalysisInfo info, JsonWriter localWriter) throws IOException {
    JsonWriter allTypes = localWriter.name("allTypes").beginArray();
    for (String allType : info.getAllTypes()) {
      allTypes.value(allType);
    }
    allTypes.endArray();
  }

  private void writeClassUsageCsv(String canonical) throws IOException {
    DefaultCallGraph defaultCallGraph = new DefaultCallGraph();
    for (Map.Entry<String, Set<String>> usagePerClassMap : defaultCallGraph.getUsagesPerClass().entrySet()) {
      String key = usagePerClassMap.getKey();
      Set<String> value = usagePerClassMap.getValue();
      for (String s : value) {
        String triplet = key + "," + s + "," + canonical + "\n";
        FileUtils.write(classUsageFile, triplet, Charset.defaultCharset(), true);
      }
    }
  }

  @Override
  public Node read(JsonReader jsonReader) {
    throw new UnsupportedOperationException();
  }
}
