package se.kth.depclean.util.json;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import fr.dutra.tools.maven.deptree.core.Node;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class NodeAdapter extends TypeAdapter<Node> {

    private final Set<String> usedDirectArtifactsCoordinates;
    private final Set<String> usedInheritedArtifactsCoordinates;
    private final Set<String> usedTransitiveArtifactsCoordinates;
    private final Set<String> unusedDirectArtifactsCoordinates;
    private final Set<String> unusedInheritedArtifactsCoordinates;
    private final Set<String> unusedTransitiveArtifactsCoordinates;

    private final Map<String, Long> sizeOfDependencies;

    public NodeAdapter(Set<String> usedDirectArtifactsCoordinates,
                       Set<String> usedInheritedArtifactsCoordinates,
                       Set<String> usedTransitiveArtifactsCoordinates,
                       Set<String> unusedDirectArtifactsCoordinates,
                       Set<String> unusedInheritedArtifactsCoordinates,
                       Set<String> unusedTransitiveArtifactsCoordinates,
                       Map<String, Long> sizeOfDependencies) {
        this.usedDirectArtifactsCoordinates = usedDirectArtifactsCoordinates;
        this.usedInheritedArtifactsCoordinates = usedInheritedArtifactsCoordinates;
        this.usedTransitiveArtifactsCoordinates = usedTransitiveArtifactsCoordinates;
        this.unusedDirectArtifactsCoordinates = unusedDirectArtifactsCoordinates;
        this.unusedInheritedArtifactsCoordinates = unusedInheritedArtifactsCoordinates;
        this.unusedTransitiveArtifactsCoordinates = unusedTransitiveArtifactsCoordinates;
        this.sizeOfDependencies = sizeOfDependencies;

        // System.out.println(usedDirectArtifactsCoordinates);
        // System.out.println(unusedDirectArtifactsCoordinates);
    }

    @Override
    public void write(JsonWriter jsonWriter, Node node) throws IOException {
        String coordinates = node.getGroupId() + ":" + node.getArtifactId() + ":" + node.getVersion() + ":" + node.getScope();
        String dependencyJar = node.getArtifactId() + "-" + node.getVersion() + ".jar";
        jsonWriter.beginObject()
                .name("id")
                .jsonValue("\"" + node.getArtifactCanonicalForm() + "\"")

                .name("coordinates")
                .jsonValue("\"" + node.getGroupId() + ":" + node.getArtifactId() + ":" + node.getVersion() + "\"")

                .name("groupId")
                .jsonValue("\"" + node.getGroupId() + "\"")

                .name("artifactId")
                .jsonValue("\"" + node.getArtifactId() + "\"")

                .name("version")
                .jsonValue("\"" + node.getVersion() + "\"")

                .name("scope")
                .jsonValue("\"" + node.getScope() + "\"")

                .name("packaging")
                .jsonValue("\"" + node.getPackaging() + "\"")

                .name("omitted")
                .jsonValue("\"" + node.isOmitted() + "\"")

                .name("classifier")
                .jsonValue("\"" + node.getClassifier() + "\"")

                .name("size")
                .jsonValue(String.valueOf(sizeOfDependencies.get(dependencyJar)))

                .name("type")
                .jsonValue((usedDirectArtifactsCoordinates.contains(coordinates) || unusedDirectArtifactsCoordinates.contains(coordinates)) ? "\"" + "direct" + "\"" :
                        (usedInheritedArtifactsCoordinates.contains(coordinates) || unusedInheritedArtifactsCoordinates.contains(coordinates)) ? "\"" + "inherited" + "\"" :
                                (usedTransitiveArtifactsCoordinates.contains(coordinates) || unusedTransitiveArtifactsCoordinates.contains(coordinates)) ? "\"" + "transitive" + "\"" :
                                        "\"" + "unknown" + "\"")

                .name("status")
                .jsonValue((usedDirectArtifactsCoordinates.contains(coordinates) || usedInheritedArtifactsCoordinates.contains(coordinates) || usedTransitiveArtifactsCoordinates.contains(coordinates)) ?
                        "\"" + "used" + "\"" :
                        (unusedDirectArtifactsCoordinates.contains(coordinates) || unusedInheritedArtifactsCoordinates.contains(coordinates) || unusedTransitiveArtifactsCoordinates.contains(coordinates)) ?
                                "\"" + "bloated" + "\"" :
                                "\"" + "unknown" + "\"")

                .name("parent")
                .jsonValue(node.getParent() != null ? "\"" + node.getParent().getArtifactCanonicalForm() + "\"" : "\"" + "null" + "\"")

                .name("children")
                .beginArray();
        for (Node c : node.getChildNodes()) {
            this.write(jsonWriter, c);
        }
        jsonWriter.endArray()
                .endObject();
    }

    @Override
    public Node read(JsonReader jsonReader) {
        throw new UnsupportedOperationException();
    }
}
