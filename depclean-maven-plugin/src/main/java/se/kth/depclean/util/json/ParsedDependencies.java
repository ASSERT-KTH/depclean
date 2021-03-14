package se.kth.depclean.util.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.dutra.tools.maven.deptree.core.InputType;
import fr.dutra.tools.maven.deptree.core.Node;
import fr.dutra.tools.maven.deptree.core.ParseException;
import fr.dutra.tools.maven.deptree.core.Parser;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import se.kth.depclean.core.analysis.DefaultProjectDependencyAnalyzer;

@Slf4j
public class ParsedDependencies {

  private final String treeTextFilePath;
  private final DefaultProjectDependencyAnalyzer dependencyAnalyzer;
  private final Set<String> usedDirectArtifactsCoordinates;
  private final Set<String> usedInheritedArtifactsCoordinates;
  private final Set<String> usedTransitiveArtifactsCoordinates;
  private final Set<String> unusedDirectArtifactsCoordinates;
  private final Set<String> unusedInheritedArtifactsCoordinates;
  private final Set<String> unusedTransitiveArtifactsCoordinates;

  private final Map<String, Long> sizeOfDependencies;
  private final File classUsageFile;

  public ParsedDependencies(String treeTextFilePath,
      Map<String, Long> sizeOfDependencies,
      DefaultProjectDependencyAnalyzer dependencyAnalyzer,
      Set<String> usedDirectArtifactsCoordinates,
      Set<String> usedInheritedArtifactsCoordinates,
      Set<String> usedUndeclaredArtifactsCoordinates,
      Set<String> unusedDirectArtifactsCoordinates,
      Set<String> unusedInheritedArtifactsCoordinates,
      Set<String> unusedUndeclaredArtifactsCoordinates,
      File classUsageFile) {
    this.treeTextFilePath = treeTextFilePath;
    this.sizeOfDependencies = sizeOfDependencies;
    this.dependencyAnalyzer = dependencyAnalyzer;
    this.usedDirectArtifactsCoordinates = usedDirectArtifactsCoordinates;
    this.usedInheritedArtifactsCoordinates = usedInheritedArtifactsCoordinates;
    this.usedTransitiveArtifactsCoordinates = usedUndeclaredArtifactsCoordinates;
    this.unusedDirectArtifactsCoordinates = unusedDirectArtifactsCoordinates;
    this.unusedInheritedArtifactsCoordinates = unusedInheritedArtifactsCoordinates;
    this.unusedTransitiveArtifactsCoordinates = unusedUndeclaredArtifactsCoordinates;
    this.classUsageFile = classUsageFile;
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
        sizeOfDependencies,
        dependencyAnalyzer,
        classUsageFile
    );
    GsonBuilder gsonBuilder = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(Node.class, nodeAdapter);
    Gson gson = gsonBuilder.create();

    return gson.toJson(tree);
  }
}
