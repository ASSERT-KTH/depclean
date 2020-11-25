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
import java.util.Map;
import java.util.Set;

@Slf4j
public class ParsedDependencies {

    private final String treeTextFilePath;
    private final Set<String> usedDirectArtifactsCoordinates;
    private final Set<String> usedInheritedArtifactsCoordinates;
    private final Set<String> usedTransitiveArtifactsCoordinates;
    private final Set<String> unusedDirectArtifactsCoordinates;
    private final Set<String> unusedInheritedArtifactsCoordinates;
    private final Set<String> unusedTransitiveArtifactsCoordinates;

    private final Map<String, Long> sizeOfDependencies;

    public ParsedDependencies(String treeTextFilePath,
                              Map<String, Long> sizeOfDependencies,
                              Set<String> usedDirectArtifactsCoordinates,
                              Set<String> usedInheritedArtifactsCoordinates,
                              Set<String> usedUndeclaredArtifactsCoordinates,
                              Set<String> unusedDirectArtifactsCoordinates,
                              Set<String> unusedInheritedArtifactsCoordinates,
                              Set<String> unusedUndeclaredArtifactsCoordinates
                             ) {
        this.treeTextFilePath = treeTextFilePath;
        this.sizeOfDependencies = sizeOfDependencies;
        this.usedDirectArtifactsCoordinates = usedDirectArtifactsCoordinates;
        this.usedInheritedArtifactsCoordinates = usedInheritedArtifactsCoordinates;
        this.usedTransitiveArtifactsCoordinates = usedUndeclaredArtifactsCoordinates;
        this.unusedDirectArtifactsCoordinates = unusedDirectArtifactsCoordinates;
        this.unusedInheritedArtifactsCoordinates = unusedInheritedArtifactsCoordinates;
        this.unusedTransitiveArtifactsCoordinates = unusedUndeclaredArtifactsCoordinates;
    }

    public String parseTreeToJSON() throws ParseException, IOException {
        InputType type = InputType.TEXT;
        Reader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(treeTextFilePath), StandardCharsets.UTF_8
        ));
        Parser parser = type.newParser();
        Node tree = parser.parse(r);
        NodeAdapter nodeAdapter = new NodeAdapter(
                usedDirectArtifactsCoordinates,
                usedInheritedArtifactsCoordinates,
                usedTransitiveArtifactsCoordinates,
                unusedDirectArtifactsCoordinates,
                unusedInheritedArtifactsCoordinates,
                unusedTransitiveArtifactsCoordinates,
                sizeOfDependencies
        );
        GsonBuilder gsonBuilder = new GsonBuilder()
                .registerTypeAdapter(Node.class, nodeAdapter);
        Gson gson = gsonBuilder.create();
        return gson.toJson(tree);
    }
}
