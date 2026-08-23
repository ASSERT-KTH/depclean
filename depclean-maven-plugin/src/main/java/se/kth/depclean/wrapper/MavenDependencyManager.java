package se.kth.depclean.wrapper;

import com.google.common.collect.ImmutableSet;
import fr.dutra.tools.maven.deptree.core.ParseException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import se.kth.depclean.core.AbstractDebloater;
import se.kth.depclean.core.analysis.graph.DependencyGraph;
import se.kth.depclean.core.analysis.model.ProjectDependencyAnalysis;
import se.kth.depclean.core.analysis.src.ImportsAnalyzer;
import se.kth.depclean.core.wrapper.DependencyManagerWrapper;
import se.kth.depclean.core.wrapper.LogWrapper;
import se.kth.depclean.graph.MavenDependencyGraph;
import se.kth.depclean.util.MavenDebloater;
import se.kth.depclean.util.MavenInvoker;
import se.kth.depclean.util.json.ParsedDependencies;

/** Maven's implementation of the dependency manager wrapper. */
public class MavenDependencyManager implements DependencyManagerWrapper {

  private static final String DIRECTORY_TO_COPY_DEPENDENCIES = "dependency";

  private final Log logger;
  private final MavenProject project;
  private final MavenSession session;
  private final DependencyGraphBuilder dependencyGraphBuilder;
  private final Model model;

  /** Creates the manager with an already-built model. */
  public MavenDependencyManager(
      Log logger,
      MavenProject project,
      MavenSession session,
      DependencyGraphBuilder dependencyGraphBuilder,
      Model model) {
    this.logger = logger;
    this.project = project;
    this.session = session;
    this.dependencyGraphBuilder = dependencyGraphBuilder;
    this.model = model;
  }

  /**
   * Creates the manager.
   *
   * @param logger the logger
   * @param project the maven project
   * @param session the maven session
   * @param dependencyGraphBuilder a tool to build the dependency graph
   */
  public MavenDependencyManager(
      Log logger,
      MavenProject project,
      MavenSession session,
      DependencyGraphBuilder dependencyGraphBuilder) {
    this.logger = logger;
    this.project = project;
    this.session = session;
    this.dependencyGraphBuilder = dependencyGraphBuilder;
    this.model = buildModel(project);
  }

  @Override
  public LogWrapper getLog() {
    return new LogWrapper() {
      @Override
      public void info(String message) {
        logger.info(message);
      }

      @Override
      public void error(String message) {
        logger.error(message);
      }

      @Override
      public void debug(String message) {
        logger.debug(message);
      }
    };
  }

  @Override
  public boolean isMaven() {
    return true;
  }

  @Override
  public boolean isPackagingPom() {
    return project.getPackaging().equals("pom");
  }

  @Override
  public DependencyGraph dependencyGraph() {
    ProjectBuildingRequest buildingRequest =
        new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
    buildingRequest.setProject(project);
    try {
      DependencyNode rootNode = dependencyGraphBuilder.buildDependencyGraph(buildingRequest, null);
      return new MavenDependencyGraph(project, model, rootNode);
    } catch (DependencyGraphBuilderException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public Set<Path> getOutputDirectories() {
    return Collections.singleton(Paths.get(project.getBuild().getOutputDirectory()));
  }

  @Override
  public Set<Path> getTestOutputDirectories() {
    return Collections.singleton(Paths.get(project.getBuild().getTestOutputDirectory()));
  }

  private Model buildModel(MavenProject project) {
    File pomFile = new File(project.getBasedir().getAbsolutePath() + File.separator + "pom.xml");

    /* Build Maven model to manipulate the pom */
    final Model builtModel;
    Reader reader;
    MavenXpp3Reader mavenReader = new MavenXpp3Reader();
    try {
      reader = new InputStreamReader(new FileInputStream(pomFile), StandardCharsets.UTF_8);
      builtModel = mavenReader.read(reader);
      builtModel.setPomFile(pomFile);
    } catch (Exception ex) {
      getLog().error("Unable to build the maven project.");
      throw new IllegalStateException(ex);
    }
    return builtModel;
  }

  /**
   * Maven processors are defined like this.
   *
   * <pre>{@code
   * <plugin>
   *   <groupId>org.bsc.maven</groupId>
   *   <artifactId>maven-processor-plugin</artifactId>
   *   <executions>
   *     <execution>
   *       <id>process</id>
   *       [...]
   *       <configuration>
   *         <processors>
   *           <processor>XXXProcessor</processor>
   *         </processors>
   *       </configuration>
   *     </execution>
   *   </executions>
   * </plugin>
   * }</pre>
   */
  @Override
  public Set<String> collectUsedClassesFromProcessors() {
    getLog().debug("# collectUsedClassesFromProcessors()");
    return Optional.ofNullable(project.getPlugin("org.bsc.maven:maven-processor-plugin"))
        .map(plugin -> plugin.getExecutionsAsMap().get("process"))
        .map(exec -> (Xpp3Dom) exec.getConfiguration())
        .map(config -> config.getChild("processors"))
        .map(Xpp3Dom::getChildren)
        .map(arr -> Arrays.stream(arr).map(Xpp3Dom::getValue).collect(Collectors.toSet()))
        .orElse(ImmutableSet.of());
  }

  @Override
  public Path getDependenciesDirectory() {
    String dependencyDirectoryName =
        project.getBuild().getDirectory() + "/" + DIRECTORY_TO_COPY_DEPENDENCIES;
    return new File(dependencyDirectoryName).toPath();
  }

  @Override
  public Set<String> collectUsedClassesFromSource(Path sourceDirectory, Path testSourceDirectory) {
    Set<String> allImports = new HashSet<>();
    ImportsAnalyzer importsInSourceFolder = new ImportsAnalyzer(sourceDirectory);
    ImportsAnalyzer importsInTestsFolder = new ImportsAnalyzer(testSourceDirectory);
    Set<String> importsInSourceFolderSet = importsInSourceFolder.collectImportedClassesFromSource();
    Set<String> importsInTestsFolderSet = importsInTestsFolder.collectImportedClassesFromSource();
    allImports.addAll(importsInSourceFolderSet);
    allImports.addAll(importsInTestsFolderSet);
    return allImports;
  }

  @Override
  public Set<Path> getResourcesDirectories() {
    Set<Path> directories =
        project.getBuild().getResources().stream()
            .map(resource -> resolveAgainstBasedir(resource.getDirectory()))
            .collect(Collectors.toCollection(HashSet::new));
    directories.add(getWebappDirectory());
    return directories;
  }

  @Override
  public Set<Path> getTestResourcesDirectories() {
    return project.getBuild().getTestResources().stream()
        .map(resource -> resolveAgainstBasedir(resource.getDirectory()))
        .collect(Collectors.toSet());
  }

  /**
   * The webapp source directory (containing web.xml), honoring a custom {@code warSourceDirectory}
   * configured on the maven-war-plugin.
   */
  private Path getWebappDirectory() {
    String warSourceDirectory =
        Optional.ofNullable(project.getPlugin("org.apache.maven.plugins:maven-war-plugin"))
            .map(plugin -> (Xpp3Dom) plugin.getConfiguration())
            .map(config -> config.getChild("warSourceDirectory"))
            .map(Xpp3Dom::getValue)
            .orElse("src/main/webapp");
    return resolveAgainstBasedir(warSourceDirectory);
  }

  private Path resolveAgainstBasedir(String directory) {
    Path path = Paths.get(directory);
    return path.isAbsolute() ? path : project.getBasedir().toPath().resolve(path);
  }

  @Override
  public AbstractDebloater<? extends Serializable> getDebloater(
      ProjectDependencyAnalysis analysis) {
    return new MavenDebloater(analysis, project, model);
  }

  @Override
  public Path getBuildDirectory() {
    return Paths.get(project.getBuild().getDirectory());
  }

  @Override
  public Path getSourceDirectory() {
    return new File(project.getBuild().getSourceDirectory()).toPath();
  }

  @Override
  public Path getTestDirectory() {
    return new File(project.getBuild().getTestSourceDirectory()).toPath();
  }

  @Override
  public void generateDependencyTree(File treeFile) throws IOException, InterruptedException {
    MavenInvoker.runCommand(
        Arrays.asList("mvn", "dependency:tree", "-DoutputFile=" + treeFile, "-Dverbose=true"),
        null);
  }

  @Override
  public String getTreeAsJson(
      File treeFile,
      ProjectDependencyAnalysis analysis,
      File classUsageFile,
      boolean createCallGraphCsv) {
    try {
      return new ParsedDependencies(treeFile, analysis, classUsageFile, createCallGraphCsv)
          .parseTreeToJson();
    } catch (ParseException | IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
