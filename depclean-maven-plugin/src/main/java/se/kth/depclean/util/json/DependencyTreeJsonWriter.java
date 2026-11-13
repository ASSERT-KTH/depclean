package se.kth.depclean.util.json;

import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.jspecify.annotations.Nullable;
import se.kth.depclean.core.analysis.model.DependencyAnalysisInfo;
import se.kth.depclean.core.analysis.model.ProjectDependencyAnalysis;

/**
 * Writes the dependency tree of a project, annotated with the results of the analysis, as JSON. The
 * tree is the one Maven resolved for the running build, so it is consistent with the analysis and
 * no second Maven has to be started to obtain it.
 */
public final class DependencyTreeJsonWriter {

  private final ProjectDependencyAnalysis analysis;
  @Nullable private final Writer callGraphWriter;
  private final Set<String> callGraphDependenciesWritten = new HashSet<>();
  @Nullable private Map<String, Set<String>> originsByTarget;

  /**
   * Creates a writer.
   *
   * @param analysis the results of the analysis
   * @param callGraphWriter where the edges of the class usage graph are appended as CSV lines
   *     ({@code OriginClass,TargetClass,TargetDependency}), or null to not write them
   */
  public DependencyTreeJsonWriter(
      ProjectDependencyAnalysis analysis, @Nullable Writer callGraphWriter) {
    this.analysis = analysis;
    this.callGraphWriter = callGraphWriter;
  }

  /**
   * Serializes a dependency tree.
   *
   * @param root the node of the project
   * @return the JSON document
   * @throws IOException if the call graph cannot be written
   */
  public String write(DependencyNode root) throws IOException {
    StringWriter out = new StringWriter();
    JsonWriter json = new JsonWriter(out);
    json.setIndent("  ");
    json.setSerializeNulls(false);
    writeNode(json, root, null);
    json.flush();
    return out.toString();
  }

  private void writeNode(JsonWriter json, DependencyNode node, @Nullable DependencyNode parent)
      throws IOException {
    Artifact artifact = node.getArtifact();
    String ga = artifact.getGroupId() + ":" + artifact.getArtifactId();
    String gav = ga + ":" + artifact.getBaseVersion();
    String canonical = canonicalForm(artifact) + ":" + artifact.getScope();
    DependencyAnalysisInfo info = analysis.getDependencyInfo(gav);
    if (info == null) {
      // not a dependency the analysis knows
      return;
    }
    if (callGraphWriter != null && callGraphDependenciesWritten.add(canonical)) {
      writeCallGraphCsv(callGraphWriter, canonical, info);
    }
    json.beginObject()
        .name("id")
        .value(canonical)
        .name("coordinates")
        .value(gav)
        .name("groupId")
        .value(artifact.getGroupId())
        .name("artifactId")
        .value(artifact.getArtifactId())
        .name("version")
        .value(artifact.getBaseVersion())
        .name("scope")
        .value(artifact.getScope())
        .name("packaging")
        .value(artifact.getType())
        .name("omitted")
        .value(false)
        .name("classifier")
        .value(classifierOf(artifact))
        .name("size")
        .value(info.size())
        .name("type")
        .value(info.type())
        .name("status")
        .value(info.status())
        .name("parent")
        .value(parent == null ? "unknown" : parentForm(parent.getArtifact()));
    json.name("allTypes").beginArray();
    for (String type : info.allTypes()) {
      json.value(type);
    }
    json.endArray();
    json.name("usedTypes").beginArray();
    for (String type : info.usedTypes()) {
      json.value(type);
    }
    json.endArray();
    json.name("usageRatio")
        .value(
            info.allTypes().isEmpty()
                ? 0.0
                : (double) info.usedTypes().size() / info.allTypes().size());
    json.name("children").beginArray();
    for (DependencyNode child : node.getChildren()) {
      writeNode(json, child, node);
    }
    json.endArray().endObject();
  }

  @Nullable
  private static String classifierOf(Artifact artifact) {
    String classifier = artifact.getClassifier();
    return classifier == null || classifier.isEmpty() ? null : classifier;
  }

  /** {@code groupId:artifactId:type[:classifier]:version}. */
  private static String canonicalForm(Artifact artifact) {
    String classifier = classifierOf(artifact);
    return artifact.getGroupId()
        + ":"
        + artifact.getArtifactId()
        + ":"
        + artifact.getType()
        + (classifier == null ? "" : ":" + classifier)
        + ":"
        + artifact.getBaseVersion();
  }

  private static String parentForm(Artifact parent) {
    String scope = parent.getScope();
    return canonicalForm(parent) + (scope == null || scope.isEmpty() ? "" : ":" + scope);
  }

  /**
   * Writes the edges of the class usage graph that target a class of the given dependency. Origins
   * and targets are written in a stable order.
   */
  private void writeCallGraphCsv(Writer writer, String canonical, DependencyAnalysisInfo info)
      throws IOException {
    final Map<String, Set<String>> origins = originsByTarget();
    final Map<String, Set<String>> targetsByOrigin = new TreeMap<>();
    for (String target : info.allTypes()) {
      for (String origin : origins.getOrDefault(target, new HashSet<>())) {
        targetsByOrigin.computeIfAbsent(origin, k -> new TreeSet<>()).add(target);
      }
    }
    for (Map.Entry<String, Set<String>> edges : targetsByOrigin.entrySet()) {
      for (String target : edges.getValue()) {
        writer.write(edges.getKey() + "," + target + "," + canonical + "\n");
      }
    }
  }

  private Map<String, Set<String>> originsByTarget() {
    if (originsByTarget == null) {
      Map<String, Set<String>> inverted = new HashMap<>();
      for (Map.Entry<String, Set<String>> usages :
          analysis.getClassUsageGraph().getUsagesPerClass().entrySet()) {
        for (String target : usages.getValue()) {
          inverted.computeIfAbsent(target, k -> new HashSet<>()).add(usages.getKey());
        }
      }
      originsByTarget = inverted;
    }
    return originsByTarget;
  }
}
