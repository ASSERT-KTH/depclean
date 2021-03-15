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

/**
 * Uses the DepClean analysis results and the dependency tree of the project to produce a JSON file. This file represent
 * the structure of the dependency tree enriched with metadata of the usage or not of each dependency.
 */
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

  /**
   * Ctor.
   */
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

  /**
   * Creates string with the JSON representation of the enriched dependency tree of the Maven project.
   *
   * @return The JSON representation of the dependency tree of the project with additional metadata of the used/unused
   *        dependencies.
   * @throws ParseException if there are parsing errors.
   * @throws IOException    if the JSON file cannot be written.
   */
  public String parseTreeToJson() throws ParseException, IOException {
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
