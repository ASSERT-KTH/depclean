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

import fr.dutra.tools.maven.deptree.core.ParseException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.CollectingDependencyNodeVisitor;
import se.kth.depclean.core.analysis.DefaultProjectDependencyAnalyzer;
import se.kth.depclean.core.analysis.ProjectDependencyAnalysis;
import se.kth.depclean.core.analysis.ProjectDependencyAnalyzerException;
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
  @Parameter(property = "creatPomDebloated", defaultValue = "false")
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

  @Component
  private ProjectBuilder mavenProjectBuilder;

  @Component
  private RepositorySystem repositorySystem;

  @Component(hint = "default")
  private DependencyGraphBuilder dependencyGraphBuilder;


  /**
   * Write pom file to the filesystem.
   *
   * @param pomFile The path to the pom.
   * @param model   The maven model to get the pom from.
   * @throws IOException In case of any IO issue.
   */
  private static void writePom(final Path pomFile, final Model model) throws IOException {
    MavenXpp3Writer writer = new MavenXpp3Writer();
    writer.write(Files.newBufferedWriter(pomFile), model);
  }

  /**
   * Print the status of the depenencies to the standard output. The format is: "[coordinates][scope] [(size)]"
   *
   * @param sizeOfDependencies A map with the size of the dependencies.
   * @param dependencies       The set dependencies to print.
   */
  private void printDependencies(Map<String, Long> sizeOfDependencies, Set<String> dependencies) {
    dependencies
        .stream()
        .sorted(Comparator.comparing(o -> getSizeOfDependency(sizeOfDependencies, o)))
        .collect(Collectors.toCollection(LinkedList::new))
        .descendingIterator()
        .forEachRemaining(s -> printString("\t" + s + " (" + getSize(s, sizeOfDependencies) + ")"));
  }

  /**
   * Util function to print the information of the analyzed artifacts.
   *
   * @param info               The usage status (used or unused) and type (direct, transitive, inherited) of artifacts.
   * @param sizeOfDependencies The size of the JAR file of the artifact.
   * @param dependencies       The GAV of the artifact.
   */
  private void printInfoOfDependencies(String info, Map<String, Long> sizeOfDependencies, Set<String> dependencies) {
    printString(info.toUpperCase() + " [" + dependencies.size() + "]" + ": ");
    printDependencies(sizeOfDependencies, dependencies);
  }

  /**
   * Utility method to obtain the size of a dependency from a map of dependency -> size. If the size of the dependency
   * cannot be obtained form the map (no key with the name of the dependency exists), then it returns 0.
   *
   * @param sizeOfDependencies A map of dependency -> size.
   * @param dependency         The coordinates of a dependency.
   * @return The size of the dependency if its name is a key in the map, otherwise it returns 0.
   */
  private Long getSizeOfDependency(Map<String, Long> sizeOfDependencies, String dependency) {
    Long size = sizeOfDependencies
        .get(dependency.split(":")[1] + "-" + dependency.split(":")[2] + ".jar");
    if (size != null) {
      return size;
    } else {
      // The name of the dependency does not match with the name of the download jar, so we keep assume the size
      // cannot be obtained and return 0.
      return Long.valueOf(0);
    }
  }

  /**
   * Get the size of the dependency in human readable format.
   *
   * @param dependency         The dependency.
   * @param sizeOfDependencies A map with the size of the dependencies, keys are stored as the downloaded jar file i.e.,
   *                           [artifactId]-[version].jar
   * @return The human readable representation of the dependency size.
   */
  private String getSize(String dependency, Map<String, Long> sizeOfDependencies) {
    String dep = dependency.split(":")[1] + "-" + dependency.split(":")[2] + ".jar";
    if (sizeOfDependencies.containsKey(dep)) {
      return FileUtils.byteCountToDisplaySize(sizeOfDependencies.get(dep));
    } else {
      // The size cannot be obtained.
      return "size unknown";
    }
  }

  /**
   * Exclude artifacts with specific scopes from the analysis.
   *
   * @param artifacts The set of artifacts to analyze.
   * @return The set of artifacts for which the scope has not been excluded.
   */
  private Set<Artifact> excludeScope(Set<Artifact> artifacts) {
    Set<Artifact> nonExcludedArtifacts = new HashSet<>();
    for (Artifact artifact : artifacts) {
      if (!ignoreScopes.contains(artifact.getScope())) {
        nonExcludedArtifacts.add(artifact);
      }
    }
    return nonExcludedArtifacts;
  }

  /**
   * Determine if an artifact is a direct or transitive child of a dependency.
   *
   * @param artifact   The artifact.
   * @param dependency The dependency
   * @return true if the artifact is a child of a dependency in the dependency tree.
   * @throws DependencyGraphBuilderException If the graph cannot be constructed.
   */
  private boolean isChildren(Artifact artifact, Dependency dependency)
      throws DependencyGraphBuilderException {
    List<DependencyNode> dependencyNodes = getDependencyNodes();
    for (DependencyNode node : dependencyNodes) {
      Dependency dependencyNode = createDependency(node.getArtifact());
      if (dependency.getGroupId().equals(dependencyNode.getGroupId())
          && dependency.getArtifactId().equals(dependencyNode.getArtifactId())) {
        // now we are in the target dependency
        for (DependencyNode child : node.getChildren()) {
          if (child.getArtifact().equals(artifact)) {
            // the dependency contains the artifact as a child node
            return true;
          }

        }
      }
    }
    return false;
  }

  /**
   * Returns a list of dependency nodes from a graph of dependency tree.
   *
   * @return The nodes in the dependency graph.
   * @throws DependencyGraphBuilderException if the graph cannot be built.
   */
  private List<DependencyNode> getDependencyNodes() throws DependencyGraphBuilderException {
    ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(
        session.getProjectBuildingRequest());
    buildingRequest.setProject(project);
    DependencyNode rootNode = dependencyGraphBuilder.buildDependencyGraph(buildingRequest, null);
    CollectingDependencyNodeVisitor visitor = new CollectingDependencyNodeVisitor();
    rootNode.accept(visitor);
    return visitor.getNodes();
  }

  /**
   * This method creates a {@link org.apache.maven.model.Dependency} object from a Maven {@link
   * org.apache.maven.artifact.Artifact}.
   *
   * @param artifact The artifact to create the dependency.
   * @return The Dependency object.
   */
  private Dependency createDependency(final Artifact artifact) {
    Dependency dependency = new Dependency();
    dependency.setGroupId(artifact.getGroupId());
    dependency.setArtifactId(artifact.getArtifactId());
    dependency.setVersion(artifact.getVersion());
    if (artifact.hasClassifier()) {
      dependency.setClassifier(artifact.getClassifier());
    }
    dependency.setOptional(artifact.isOptional());
    dependency.setScope(artifact.getScope());
    dependency.setType(artifact.getType());
    return dependency;
  }

  private void printString(String string) {
    System.out.println(string); //NOSONAR avoid a warning of non-used logger
  }

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
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
      } catch (IOException e) {
        getLog().error("Error copying directory libs to dependency");
      }
    }

    /* Get the size of all the dependencies */
    Map<String, Long> sizeOfDependencies = new HashMap<>();
    // First, add the size of the project, as the sum of all the files in target/classes
    String projectJar = project.getArtifactId() + "-" + project.getVersion() + ".jar";
    long projectSize = FileUtils.sizeOf(new File(project.getBuild().getOutputDirectory()));
    sizeOfDependencies.put(projectJar, projectSize);
    if (Files.exists(Paths.get(
        project.getBuild().getDirectory() + File.separator + DIRECTORY_TO_COPY_DEPENDENCIES))) {
      Iterator<File> iterator = FileUtils.iterateFiles(
          new File(
              project.getBuild().getDirectory() + File.separator
                  + DIRECTORY_TO_COPY_DEPENDENCIES), new String[] {"jar"}, true);
      while (iterator.hasNext()) {
        File file = iterator.next();
        sizeOfDependencies.put(file.getName(), FileUtils.sizeOf(file));
      }
    } else {
      log.warn("Dependencies where not copied locally");
    }

    /* Decompress dependencies */
    String dependencyDirectoryName =
        project.getBuild().getDirectory() + "/" + DIRECTORY_TO_COPY_DEPENDENCIES;
    File dependencyDirectory = new File(dependencyDirectoryName);
    if (dependencyDirectory.exists()) {
      JarUtils.decompressJars(dependencyDirectoryName);
    }

    /* Analyze dependencies usage status */
    ProjectDependencyAnalysis projectDependencyAnalysis;
    DefaultProjectDependencyAnalyzer dependencyAnalyzer = new DefaultProjectDependencyAnalyzer(ignoreTests);
    try {
      projectDependencyAnalysis = dependencyAnalyzer.analyze(project);
    } catch (ProjectDependencyAnalyzerException e) {
      getLog().error("Unable to analyze dependencies.");
      return;
    }

    Set<Artifact> usedTransitiveArtifacts = projectDependencyAnalysis.getUsedUndeclaredArtifacts();
    Set<Artifact> usedDirectArtifacts = projectDependencyAnalysis.getUsedDeclaredArtifacts();
    Set<Artifact> unusedDirectArtifacts = projectDependencyAnalysis.getUnusedDeclaredArtifacts();
    Set<Artifact> unusedTransitiveArtifacts = project.getArtifacts();

    unusedTransitiveArtifacts.removeAll(usedDirectArtifacts);
    unusedTransitiveArtifacts.removeAll(usedTransitiveArtifacts);
    unusedTransitiveArtifacts.removeAll(unusedDirectArtifacts);

    /* Exclude dependencies with specific scopes from the DepClean analysis */
    if (!ignoreScopes.isEmpty()) {
      printString("Ignoring dependencies with scope(s): " + ignoreScopes.toString());
      if (!ignoreScopes.isEmpty()) {
        usedTransitiveArtifacts = excludeScope(usedTransitiveArtifacts);
        usedDirectArtifacts = excludeScope(usedDirectArtifacts);
        unusedDirectArtifacts = excludeScope(unusedDirectArtifacts);
        unusedTransitiveArtifacts = excludeScope(unusedTransitiveArtifacts);
      }
    }

    /* Use artifacts coordinates for the report instead of the Artifact object */

    // List of dependencies declared in the POM
    List<Dependency> dependencies = model.getDependencies();
    Set<String> declaredArtifactsGroupArtifactIds = new HashSet<>();
    for (Dependency dep : dependencies) {
      declaredArtifactsGroupArtifactIds.add(dep.getGroupId() + ":" + dep.getArtifactId());
    }

    // --- used dependencies
    Set<String> usedDirectArtifactsCoordinates = new HashSet<>();
    Set<String> usedInheritedArtifactsCoordinates = new HashSet<>();
    Set<String> usedTransitiveArtifactsCoordinates = new HashSet<>();

    for (Artifact artifact : usedDirectArtifacts) {
      String artifactGroupArtifactId = artifact.getGroupId() + ":" + artifact.getArtifactId();
      String artifactGroupArtifactIds =
          artifactGroupArtifactId + ":" + artifact.toString().split(":")[3] + ":" + artifact.toString()
              .split(":")[4];
      if (declaredArtifactsGroupArtifactIds.contains(artifactGroupArtifactId)) {
        // the artifact is declared in the pom
        usedDirectArtifactsCoordinates.add(artifactGroupArtifactIds);
      } else {
        // the artifact is inherited
        usedInheritedArtifactsCoordinates.add(artifactGroupArtifactIds);
      }
    }

    // TODO Fix: The used transitive dependencies induced by inherited dependencies should be considered
    //  as used inherited
    for (Artifact artifact : usedTransitiveArtifacts) {
      String artifactGroupArtifactId = artifact.getGroupId() + ":" + artifact.getArtifactId();
      String artifactGroupArtifactIds =
          artifactGroupArtifactId + ":" + artifact.toString().split(":")[3] + ":" + artifact.toString()
              .split(":")[4];
      usedTransitiveArtifactsCoordinates.add(artifactGroupArtifactIds);
    }

    // --- unused dependencies
    Set<String> unusedDirectArtifactsCoordinates = new HashSet<>();
    Set<String> unusedInheritedArtifactsCoordinates = new HashSet<>();
    Set<String> unusedTransitiveArtifactsCoordinates = new HashSet<>();

    for (Artifact artifact : unusedDirectArtifacts) {
      String artifactGroupArtifactId = artifact.getGroupId() + ":" + artifact.getArtifactId();
      String artifactGroupArtifactIds =
          artifactGroupArtifactId + ":" + artifact.toString().split(":")[3] + ":" + artifact.toString()
              .split(":")[4];
      if (declaredArtifactsGroupArtifactIds.contains(artifactGroupArtifactId)) {
        // the artifact is declared in the pom
        unusedDirectArtifactsCoordinates.add(artifactGroupArtifactIds);
      } else {
        // the artifact is inherited
        unusedInheritedArtifactsCoordinates.add(artifactGroupArtifactIds);
      }
    }

    // TODO Fix: The unused transitive dependencies induced by inherited dependencies should be considered as
    //  unused inherited
    for (Artifact artifact : unusedTransitiveArtifacts) {
      String artifactGroupArtifactId = artifact.getGroupId() + ":" + artifact.getArtifactId();
      String artifactGroupArtifactIds =
          artifactGroupArtifactId + ":" + artifact.toString().split(":")[3] + ":" + artifact.toString()
              .split(":")[4];
      unusedTransitiveArtifactsCoordinates.add(artifactGroupArtifactIds);
    }

    /* Ignoring dependencies from the analysis */
    if (ignoreDependencies != null) {
      for (String ignoredDependency : ignoreDependencies) {
        // if the ignored dependency is an unused direct dependency then add it to the set of used direct
        // and remove it from the set of unused direct
        for (Iterator<String> i = unusedDirectArtifactsCoordinates.iterator(); i.hasNext(); ) {
          String unusedDirectArtifact = i.next();
          if (ignoredDependency.equals(unusedDirectArtifact)) {
            usedDirectArtifactsCoordinates.add(unusedDirectArtifact);
            i.remove();
            break;
          }
        }
        // if the ignored dependency is an unused inherited dependency then add it to the set of used inherited
        // and remove it from the set of unused inherited
        for (Iterator<String> j = unusedInheritedArtifactsCoordinates.iterator(); j.hasNext(); ) {
          String unusedInheritedArtifact = j.next();
          if (ignoredDependency.equals(unusedInheritedArtifact)) {
            usedInheritedArtifactsCoordinates.add(unusedInheritedArtifact);
            j.remove();
            break;
          }
        }
        // if the ignored dependency is an unused transitive dependency then add it to the set of used transitive
        // and remove it from the set of unused transitive
        for (Iterator<String> j = unusedTransitiveArtifactsCoordinates.iterator(); j.hasNext(); ) {
          String unusedTransitiveArtifact = j.next();
          if (ignoredDependency.equals(unusedTransitiveArtifact)) {
            usedTransitiveArtifactsCoordinates.add(unusedTransitiveArtifact);
            j.remove();
            break;
          }
        }
      }
    }

    /* Printing the results to the terminal */
    printString(SEPARATOR);
    printString(" D E P C L E A N   A N A L Y S I S   R E S U L T S");
    printString(SEPARATOR);
    printInfoOfDependencies("Used direct dependencies", sizeOfDependencies, usedDirectArtifactsCoordinates);
    printInfoOfDependencies("Used inherited dependencies", sizeOfDependencies, usedInheritedArtifactsCoordinates);
    printInfoOfDependencies("Used transitive dependencies", sizeOfDependencies, usedTransitiveArtifactsCoordinates);
    printInfoOfDependencies("Potentially unused direct dependencies", sizeOfDependencies,
        unusedDirectArtifactsCoordinates);
    printInfoOfDependencies("Potentially unused inherited dependencies", sizeOfDependencies,
        unusedInheritedArtifactsCoordinates);
    printInfoOfDependencies("Potentially unused transitive dependencies", sizeOfDependencies,
        unusedTransitiveArtifactsCoordinates);

    if (!ignoreDependencies.isEmpty()) {
      printString(SEPARATOR);
      printString(
          "Dependencies ignored in the analysis by the user"
              + " [" + ignoreDependencies.size() + "]" + ":" + " ");
      ignoreDependencies.stream().forEach(s -> printString("\t" + s));
    }

    /* Fail the build if there are unused direct dependencies */
    if (failIfUnusedDirect && !unusedDirectArtifactsCoordinates.isEmpty()) {
      throw new MojoExecutionException(
          "Build failed due to unused direct dependencies in the dependency tree of the project.");
    }

    /* Fail the build if there are unused direct dependencies */
    if (failIfUnusedTransitive && !unusedTransitiveArtifactsCoordinates.isEmpty()) {
      throw new MojoExecutionException(
          "Build failed due to unused transitive dependencies in the dependency tree of the project.");
    }

    /* Fail the build if there are unused direct dependencies */
    if (failIfUnusedInherited && !unusedInheritedArtifactsCoordinates.isEmpty()) {
      throw new MojoExecutionException(
          "Build failed due to unused inherited dependencies in the dependency tree of the project.");
    }

    /* Writing the debloated version of the pom */
    if (createPomDebloated) {
      getLog().info("Starting debloating POM");

      /* Add used transitive as direct dependencies */
      try {
        if (!usedTransitiveArtifacts.isEmpty()) {
          getLog()
              .info("Adding " + unusedTransitiveArtifactsCoordinates.size()
                  + " used transitive dependencies as direct dependencies.");
          for (Artifact usedUndeclaredArtifact : usedTransitiveArtifacts) {
            model.addDependency(createDependency(usedUndeclaredArtifact));
          }
        }
      } catch (Exception e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }

      /* Remove unused direct dependencies */
      try {
        if (!unusedDirectArtifacts.isEmpty()) {
          getLog().info("Removing " + unusedDirectArtifactsCoordinates.size()
              + " unused direct dependencies.");
          for (Artifact unusedDeclaredArtifact : unusedDirectArtifacts) {
            for (Dependency dependency : model.getDependencies()) {
              if (dependency.getGroupId().equals(unusedDeclaredArtifact.getGroupId())
                  && dependency.getArtifactId().equals(unusedDeclaredArtifact.getArtifactId())) {
                model.removeDependency(dependency);
                break;
              }
            }
          }
        }
      } catch (Exception e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }

      /* Exclude unused transitive dependencies */
      try {
        if (!unusedTransitiveArtifacts.isEmpty()) {
          getLog().info(
              "Excluding " + unusedTransitiveArtifacts.size()
                  + " unused transitive dependencies one-by-one.");
          for (Dependency dependency : model.getDependencies()) {
            for (Artifact artifact : unusedTransitiveArtifacts) {
              if (isChildren(artifact, dependency)) {
                getLog().info("Excluding " + artifact.toString() + " from dependency " + dependency
                    .toString());
                Exclusion exclusion = new Exclusion();
                exclusion.setGroupId(artifact.getGroupId());
                exclusion.setArtifactId(artifact.getArtifactId());
                dependency.addExclusion(exclusion);
              }
            }
          }
        }
      } catch (Exception e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }

      /* Write the debloated pom file */
      String pathToDebloatedPom =
          project.getBasedir().getAbsolutePath() + File.separator + "pom-debloated.xml";
      try {
        Path path = Paths.get(pathToDebloatedPom);
        writePom(path, model);
      } catch (IOException e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
      getLog().info("POM debloated successfully");
      getLog().info("pom-debloated.xml file created in: " + pathToDebloatedPom);
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
          sizeOfDependencies,
          dependencyAnalyzer,
          usedDirectArtifactsCoordinates,
          usedInheritedArtifactsCoordinates,
          usedTransitiveArtifactsCoordinates,
          unusedDirectArtifactsCoordinates,
          unusedInheritedArtifactsCoordinates,
          unusedTransitiveArtifactsCoordinates,
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
  }
}
