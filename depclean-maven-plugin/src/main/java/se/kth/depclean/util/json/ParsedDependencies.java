package se.kth.depclean.util.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.dutra.tools.maven.deptree.core.InputType;
import fr.dutra.tools.maven.deptree.core.Node;
import fr.dutra.tools.maven.deptree.core.ParseException;
import fr.dutra.tools.maven.deptree.core.Parser;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Slf4j
public class ParsedDependencies {

    private final String treeTextFilePath;
    private final Set<String> usedDeclaredArtifactsCoordinates;
    private final Set<String> usedUndeclaredArtifactsCoordinates;

    public ParsedDependencies(String treeTextFilePath,
                              Set<String> usedDeclaredArtifactsCoordinates,
                              Set<String> usedUndeclaredArtifactsCoordinates) {
        this.treeTextFilePath = treeTextFilePath;
        this.usedDeclaredArtifactsCoordinates = usedDeclaredArtifactsCoordinates;
        this.usedUndeclaredArtifactsCoordinates = usedUndeclaredArtifactsCoordinates;
    }

    public String parseTreeToJSON() throws ParseException, IOException {
        InputType type = InputType.TEXT;
        Reader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(treeTextFilePath), StandardCharsets.UTF_8
        ));
        Parser parser = type.newParser();
        Node tree = parser.parse(r);
        NodeAdapter nodeAdapter = new NodeAdapter(
                usedDeclaredArtifactsCoordinates,
                usedUndeclaredArtifactsCoordinates
        );
        GsonBuilder gsonBuilder = new GsonBuilder()
                .registerTypeAdapter(Node.class, nodeAdapter);
        Gson gson = gsonBuilder.create();
        return gson.toJson(tree);
    }
}
