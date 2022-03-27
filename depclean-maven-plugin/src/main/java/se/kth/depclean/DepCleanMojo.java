/*
 * Copyright (c) 2020, CASTOR Software Research Centre (www.castor.kth.se)
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package se.kth.depclean;

import com.google.common.collect.ImmutableSet;
import fr.dutra.tools.maven.deptree.core.ParseException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import se.kth.depclean.core.analysis.DefaultProjectDependencyAnalyzer;
import se.kth.depclean.core.analysis.ProjectDependencyAnalysis;
import se.kth.depclean.core.analysis.ProjectDependencyAnalyzerException;
import se.kth.depclean.core.analysis.graph.DependencyGraph;
import se.kth.depclean.core.analysis.model.ClassName;
import se.kth.depclean.core.analysis.model.Dependency;
import se.kth.depclean.core.analysis.model.ProjectContext;
import se.kth.depclean.core.analysis.model.Scope;
import se.kth.depclean.graph.MavenDependencyGraph;
import se.kth.depclean.util.DebloatedPomWriter;
import se.kth.depclean.util.JarUtils;
import se.kth.depclean.util.MavenInvoker;
import se.kth.depclean.util.json.ParsedDependencies;

/**
 * This Maven mojo is the main class of DepClean. DepClean is built on top of the maven-dependency-analyzer component.
 * It produces a clean copy of the project's pom file, without bloated dependencies.
 *
 * @see <a href="https://stackoverflow.com/questions/1492000/how-to-get-access-to-mavens-dependency-hierarchy-within-a-plugin"></a>
 * @see <a href="http://maven.apache.org/guides/introduction/introduction-to-optional-and-excludes-dependencies.html"></a>
 */
@Mojo(name = "depclean", defaultPhase = LifecyclePhase.PACKAGE,
    requiresDependencyCollection = ResolutionScope.TEST,
    requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
@Slf4j
public class DepCleanMojo extends AbstractMojo {

  private static final String SEPARATOR = "-------------------------------------------------------";
  public static final String DIRECTORY_TO_COPY_DEPENDENCIES = "dependency";

  /**
   * The Maven project to analyze.
   */
  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  /**
   * The Maven session to analyze.
   */
  @Parameter(defaultValue = "${session}", readonly = true)
  private MavenSession session;

  /**
   * If this is true, DepClean creates a debloated version of the pom without unused dependencies, called
   * "debloated-pom.xml", in root of the project.
   */
  @Parameter(property = "createPomDebloated", defaultValue = "false")
  private boolean createPomDebloated;

  /**
   * If this is true, DepClean creates a JSON file with the result of the analysis. The file is called
   * "debloat-result.json" and it is located in /target.
   */
  @Parameter(property = "createResultJson", defaultValue = "false")
  private boolean createResultJson;


  /**
   * If this is true, DepClean creates a CSV file with the result of the analysis with the columns:
   * OriginClass,TargetClass,Dependency. The file is called "class-usage.csv" and it is located in /target.
   */
  @Parameter(property = "createClassUsageCsv", defaultValue = "false")
  private boolean createClassUsageCsv;

  /**
   * Add a list of dependencies, identified by their coordinates, to be ignored by DepClean during the analysis and
   * considered as used dependencies. Useful to override incomplete result caused by bytecode-level analysis Dependency
   * format is <code>groupId:artifactId:version</code>.
   */
  @Parameter(property = "ignoreDependencies")
  private Set<String> ignoreDependencies;

  /**
   * Ignore dependencies with specific scopes from the DepClean analysis.
   */
  @Parameter(property = "ignoreScopes")
  private Set<String> ignoreScopes;

  /**
   * If this is true, DepClean will not analyze the test sources in the project, and, therefore, the dependencies that
   * are only used for testing will be considered unused. This property is useful to detect dependencies that have a
   * compile scope but are only used during testing. Hence, these dependencies should have a test scope.
   */
  @Parameter(property = "ignoreTests", defaultValue = "false")
  private boolean ignoreTests;

  /**
   * If this is true, and DepClean reported any unused direct dependency in the dependency tree, then the project's
   * build fails immediately after running DepClean.
   */
  @Parameter(property = "failIfUnusedDirect", defaultValue = "false")
  private boolean failIfUnusedDirect;

  /**
   * If this is true, and DepClean reported any unused transitive dependency in the dependency tree, then the project's
   * build fails immediately after running DepClean.
   */
  @Parameter(property = "failIfUnusedTransitive", defaultValue = "false")
  private boolean failIfUnusedTransitive;

  /**
   * If this is true, and DepClean reported any unused inherited dependency in the dependency tree, then the project's
   * build fails immediately after running DepClean.
   */
  @Parameter(property = "failIfUnusedInherited", defaultValue = "false")
  private boolean failIfUnusedInherited;

  /**
   * Skip plugin execution completely.
   */
  @Parameter(property = "skipDepClean", defaultValue = "false")
  private boolean skipDepClean;

  /**
   * To build the dependency graph.
   */
  @Component(hint = "default")
  private DependencyGraphBuilder dependencyGraphBuilder;

  /**
   * Returns a list of dependency nodes from a graph of dependency tree.
   *
   * @param model The maven model
   * @return The nodes in the dependency graph.
   * @throws DependencyGraphBuilderException if the graph cannot be built.
   */
  private DependencyGraph dependencyGraph(Model model) throws DependencyGraphBuilderException {
    ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(
        session.getProjectBuildingRequest());
    buildingRequest.setProject(project);
    DependencyNode rootNode = dependencyGraphBuilder.buildDependencyGraph(buildingRequest, null);
    return new MavenDependencyGraph(project, model, rootNode);
  }

  private void printString(final String string) {
    System.out.println(string); //NOSONAR avoid a warning of non-used logger
  }

  @SneakyThrows
  @Override
  public final void execute() {
    final long startTime = System.currentTimeMillis();

    if (skipDepClean) {
      getLog().info("Skipping DepClean plugin execution");
      return;
    }

    printString(SEPARATOR);
    getLog().info("Starting DepClean dependency analysis");

    File pomFile = new File(project.getBasedir().getAbsolutePath() + File.separator + "pom.xml");

    String packaging = project.getPackaging();
    if (packaging.equals("pom")) {
      getLog().info("Skipping because packaging type " + packaging + ".");
      return;
    }

    /* Build Maven model to manipulate the pom */
    Model model;
    FileReader reader;
    MavenXpp3Reader mavenReader = new MavenXpp3Reader();
    try {
      reader = new FileReader(pomFile);
      model = mavenReader.read(reader);
      model.setPomFile(pomFile);
    } catch (Exception ex) {
      getLog().error("Unable to build the maven project.");
      return;
    }

    /* Copy direct dependencies locally */
    try {
      MavenInvoker.runCommand("mvn dependency:copy-dependencies -DoutputDirectory="
          + project.getBuild().getDirectory() + File.separator + DIRECTORY_TO_COPY_DEPENDENCIES);
    } catch (IOException | InterruptedException e) {
      getLog().error("Unable to resolve all the dependencies.");
      Thread.currentThread().interrupt();
      return;
    }

    // TODO remove this workaround later
    if (new File(project.getBuild().getDirectory() + File.separator + "libs").exists()) {
      try {
        FileUtils.copyDirectory(new File(project.getBuild().getDirectory() + File.separator + "libs"),
            new File(project.getBuild().getDirectory() + File.separator + DIRECTORY_TO_COPY_DEPENDENCIES)
        );
      } catch (IOException | NullPointerException e) {
        getLog().error("Error copying directory libs to dependency");
      }
    }

    /* Decompress dependencies */
    String dependencyDirectoryName =
        project.getBuild().getDirectory() + "/" + DIRECTORY_TO_COPY_DEPENDENCIES;
    File dependencyDirectory = new File(dependencyDirectoryName);
    if (dependencyDirectory.exists()) {
      JarUtils.decompress(dependencyDirectoryName);
    }

    /* Analyze dependencies usage status */
    final ProjectContext projectContext = buildProjectContext(model);
    final ProjectDependencyAnalysis analysis;
    final DefaultProjectDependencyAnalyzer dependencyAnalyzer = new DefaultProjectDependencyAnalyzer(projectContext);
    try {
      analysis = dependencyAnalyzer.analyze();
    } catch (ProjectDependencyAnalyzerException e) {
      getLog().error("Unable to analyze dependencies.");
      return;
    }

    analysis.print();

    /* Fail the build if there are unused direct dependencies */
    if (failIfUnusedDirect && analysis.hasUnusedDirectDependencies()) {
      throw new MojoExecutionException(
          "Build failed due to unused direct dependencies in the dependency tree of the project.");
    }

    /* Fail the build if there are unused transitive dependencies */
    if (failIfUnusedTransitive && analysis.hasUnusedTransitiveDependencies()) {
      throw new MojoExecutionException(
          "Build failed due to unused transitive dependencies in the dependency tree of the project.");
    }

    /* Fail the build if there are unused inherited dependencies */
    if (failIfUnusedInherited && analysis.hasUnusedInheritedDependencies()) {
      throw new MojoExecutionException(
          "Build failed due to unused inherited dependencies in the dependency tree of the project.");
    }

    /* Writing the debloated version of the pom */
    if (createPomDebloated) {
      new DebloatedPomWriter(project, model, analysis).write();
    }

    /* Writing the JSON file with the debloat results */
    if (createResultJson) {
      printString("Creating depclean-results.json, please wait...");
      final File jsonFile = new File(project.getBuild().getDirectory() + File.separator + "depclean-results.json");
      final File treeFile = new File(project.getBuild().getDirectory() + File.separator + "tree.txt");
      final File classUsageFile = new File(project.getBuild().getDirectory() + File.separator + "class-usage.csv");
      try {
        MavenInvoker.runCommand("mvn dependency:tree -DoutputFile=" + treeFile + " -Dverbose=true");
      } catch (IOException | InterruptedException e) {
        getLog().error("Unable to generate dependency tree.");
        // Restore interrupted state...
        Thread.currentThread().interrupt();
        return;
      }
      if (createClassUsageCsv) {
        printString("Creating class-usage.csv, please wait...");
        try {
          FileUtils.write(classUsageFile, "OriginClass,TargetClass,Dependency\n", Charset.defaultCharset());
        } catch (IOException e) {
          getLog().error("Error writing the CSV header.");
        }
      }
      ParsedDependencies parsedDependencies = new ParsedDependencies(
          treeFile,
          analysis,
          classUsageFile,
          createClassUsageCsv
      );
      try {
        FileUtils.write(jsonFile, parsedDependencies.parseTreeToJson(), Charset.defaultCharset());
      } catch (ParseException | IOException e) {
        getLog().error("Unable to generate JSON file.");
      }
      if (jsonFile.exists()) {
        getLog().info("depclean-results.json file created in: " + jsonFile.getAbsolutePath());
      }
      if (classUsageFile.exists()) {
        getLog().info("class-usage.csv file created in: " + classUsageFile.getAbsolutePath());
      }
    }

    final long stopTime = System.currentTimeMillis();
    log.info("Analysis done in " + getTime(stopTime - startTime));
  }

  private String getTime(long millis) {
    long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
    long seconds = (TimeUnit.MILLISECONDS.toSeconds(millis) % 60);

    return String.format("%smin %ss", minutes, seconds);
  }

  private ProjectContext buildProjectContext(Model model) throws DependencyGraphBuilderException {
    if (ignoreTests) {
      ignoreScopes.add("test");
    }

    final DependencyGraph dependencyGraph = dependencyGraph(model);
    return new ProjectContext(
        dependencyGraph,
        Paths.get(project.getBuild().getOutputDirectory()),
        Paths.get(project.getBuild().getTestOutputDirectory()),
        ignoreScopes.stream().map(Scope::new).collect(Collectors.toSet()),
        toDependencyCoordinates(dependencyGraph.allDependencies(), ignoreDependencies),
        collectUsedClassesFromProcessors().stream().map(ClassName::new).collect(Collectors.toSet())
    );
  }

  /**
   * Maven processors are defined like this.
   * <pre>{@code
   *       <plugin>
   *         <groupId>org.bsc.maven</groupId>
   *         <artifactId>maven-processor-plugin</artifactId>
   *         <executions>
   *           <execution>
   *             <id>process</id>
   *             [...]
   *             <configuration>
   *               <processors>
   *                 <processor>XXXProcessor</processor>
   *               </processors>
   *             </configuration>
   *           </execution>
   *         </executions>
   *       </plugin>
   * }</pre>
   */
  private Set<String> collectUsedClassesFromProcessors() {
    log.trace("# collectUsedClassesFromProcessors()");
    return Optional.ofNullable(project.getPlugin("org.bsc.maven:maven-processor-plugin"))
        .map(plugin -> plugin.getExecutionsAsMap().get("process"))
        .map(exec -> (Xpp3Dom) exec.getConfiguration())
        .map(config -> config.getChild("processors"))
        .map(Xpp3Dom::getChildren)
        .map(arr -> Arrays.stream(arr).map(Xpp3Dom::getValue).collect(Collectors.toSet()))
        .orElse(ImmutableSet.of());
  }

  /**
   * Returns a set of {@code DependencyCoordinate}s that match given string representations.
   *
   * @param allArtifacts all known artifacts
   * @param ignoreDependencies string representation of artificats to return
   * @return a set of {@code DependencyCoordinate}s that match given string representations
   */
  private Set<Dependency> toDependencyCoordinates(Set<Dependency> allArtifacts,
                                                  Set<String> ignoreDependencies) {
    return ignoreDependencies.stream()
        .map(dependency -> findDependency(allArtifacts, dependency))
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  private Dependency findDependency(Set<Dependency> allArtifacts, String dependency) {
    return allArtifacts.stream()
        .filter(artifact -> artifact.toString().toLowerCase().contains(dependency.toLowerCase()))
        .findFirst()
        .orElse(null);
  }
}
