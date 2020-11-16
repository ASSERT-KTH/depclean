package se.kth.depclean.util.json;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import fr.dutra.tools.maven.deptree.core.Node;

import java.io.IOException;
import java.util.Random;
import java.util.Set;

public class NodeAdapter extends TypeAdapter<Node> {

    private final Set<String> usedDeclaredArtifactsCoordinates;
    private final Set<String> usedUndeclaredArtifactsCoordinates;
    Random random = new Random();

    public NodeAdapter(Set<String> usedDeclaredArtifactsCoordinates,
                       Set<String> usedUndeclaredArtifactsCoordinates) {
        this.usedDeclaredArtifactsCoordinates = usedDeclaredArtifactsCoordinates;
        this.usedUndeclaredArtifactsCoordinates = usedUndeclaredArtifactsCoordinates;
    }

    @Override
    public void write(JsonWriter jsonWriter, Node node) throws IOException {
        String coordinates = node.getGroupId() + ":" + node.getArtifactId() + ":" + node.getVersion() + ":" + node.getScope();
        jsonWriter.beginObject()
                .name("coordinates")
                .jsonValue("\"" + node.getArtifactCanonicalForm() + "\"")

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
                .jsonValue(String.valueOf(random.nextDouble()))

                .name("status")
                .jsonValue((usedDeclaredArtifactsCoordinates.contains(coordinates) ||
                        usedUndeclaredArtifactsCoordinates.contains(coordinates)) ? "\"" + "used" + "\"" : "\"" + "bloated" + "\"")

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
