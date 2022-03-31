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
import lombok.AllArgsConstructor;
import se.kth.depclean.core.analysis.model.ProjectDependencyAnalysis;

/**
 * Uses the DepClean analysis results and the dependency tree of the project to produce a JSON file. This file represent
 * the structure of the dependency tree enriched with metadata of the usage or not of each dependency.
 */
@AllArgsConstructor
public class ParsedDependencies {

  private final File treeFile;
  private final ProjectDependencyAnalysis analysis;
  private final File classUsageFile;
  private final boolean createClassUsageCsv;

  /**
   * Creates string with the JSON representation of the enriched dependency tree of the Maven project.
   *
   * @return The JSON representation of the dependency tree of the project with additional metadata of the
   *         used/unused dependencies.
   *
   * @throws ParseException if there are parsing errors.
   * @throws IOException    if the JSON file cannot be written.
   */
  public String parseTreeToJson() throws ParseException, IOException {
    InputType type = InputType.TEXT;
    Reader r = new BufferedReader(new InputStreamReader(new FileInputStream(treeFile), StandardCharsets.UTF_8));
    Parser parser = type.newParser();
    Node tree = parser.parse(r);
    NodeAdapter nodeAdapter = new NodeAdapter(
        analysis,
        classUsageFile,
        createClassUsageCsv
    );
    GsonBuilder gsonBuilder = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(Node.class, nodeAdapter);
    Gson gson = gsonBuilder.create();
    return gson.toJson(tree);
  }
}
