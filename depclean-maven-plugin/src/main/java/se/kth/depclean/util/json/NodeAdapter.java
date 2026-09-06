package se.kth.depclean.util.json;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import fr.dutra.tools.maven.deptree.core.Node;
import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import se.kth.depclean.core.analysis.graph.DefaultCallGraph;
import se.kth.depclean.core.analysis.model.DependencyAnalysisInfo;
import se.kth.depclean.core.analysis.model.ProjectDependencyAnalysis;

/** Custom Gson type adapter to write a JSON file with information of the dependencies. */
public class NodeAdapter extends TypeAdapter<Node> {

  private final ProjectDependencyAnalysis analysis;
  @Nullable private final Writer callGraphWriter;
  private final Set<String> callGraphDependenciesWritten = new HashSet<>();

  /**
   * Creates the adapter.
   *
   * @param analysis the analysis results
   * @param callGraphWriter where to write the call graph CSV, or {@code null} to not write it
   */
  public NodeAdapter(ProjectDependencyAnalysis analysis, @Nullable Writer callGraphWriter) {
    this.analysis = analysis;
    this.callGraphWriter = callGraphWriter;
  }

  @Override
  public void write(JsonWriter jsonWriter, Node node) throws IOException {
    String ga = node.getGroupId() + ":" + node.getArtifactId();
    String gav = ga + ":" + node.getVersion();
    String vs = node.getVersion() + ":" + node.getScope();
    String canonical = ga + ":" + node.getPackaging() + ":" + vs;

    final DependencyAnalysisInfo dependencyInfo = analysis.getDependencyInfo(gav);

    if (dependencyInfo != null) {

      if (callGraphWriter != null && callGraphDependenciesWritten.add(canonical)) {
        writeCallGraphCsv(callGraphWriter, canonical, dependencyInfo);
      }

      JsonWriter localWriter =
          jsonWriter
              .beginObject()
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
              .value(dependencyInfo.size())
              .name("type")
              .value(dependencyInfo.type())
              .name("status")
              .value(dependencyInfo.status())
              .name("parent")
              .value(getParent(node));

      writeAllTypes(dependencyInfo, localWriter);
      writeUsedTypes(dependencyInfo, localWriter);
      writeUsageRatio(dependencyInfo, localWriter);

      for (Node c : node.getChildNodes()) {
        this.write(jsonWriter, c);
      }
      jsonWriter.endArray().endObject();
    }
  }

  private String getParent(Node node) {
    return node.getParent() != null ? node.getParent().getArtifactCanonicalForm() : "unknown";
  }

  private void writeUsageRatio(DependencyAnalysisInfo info, JsonWriter localWriter)
      throws IOException {
    localWriter
        .name("usageRatio")
        .value(
            info.allTypes().isEmpty()
                ? 0
                : ((double) info.usedTypes().size() / info.allTypes().size()))
        .name("children")
        .beginArray();
  }

  private void writeUsedTypes(DependencyAnalysisInfo info, JsonWriter localWriter)
      throws IOException {
    JsonWriter usedTypes = localWriter.name("usedTypes").beginArray();
    for (String usedType : info.usedTypes()) {
      usedTypes.value(usedType);
    }
    usedTypes.endArray();
  }

  private void writeAllTypes(DependencyAnalysisInfo info, JsonWriter localWriter)
      throws IOException {
    JsonWriter allTypes = localWriter.name("allTypes").beginArray();
    for (String allType : info.allTypes()) {
      allTypes.value(allType);
    }
    allTypes.endArray();
  }

  /**
   * Writes one {@code caller,callee,dependency} line per call-graph edge whose target class belongs
   * to the given dependency, sorted by caller then callee.
   */
  private static void writeCallGraphCsv(
      Writer writer, String canonical, DependencyAnalysisInfo dependencyInfo) throws IOException {
    final Set<String> dependencyTypes = dependencyInfo.allTypes();
    final Map<String, Set<String>> usagesPerClass =
        new TreeMap<>(DefaultCallGraph.getUsagesPerClass());
    for (Map.Entry<String, Set<String>> usages : usagesPerClass.entrySet()) {
      for (String referenced : new TreeSet<>(usages.getValue())) {
        if (dependencyTypes.contains(referenced)) {
          writer.write(usages.getKey() + "," + referenced + "," + canonical + "\n");
        }
      }
    }
  }

  @Override
  public Node read(JsonReader jsonReader) {
    throw new UnsupportedOperationException();
  }
}
