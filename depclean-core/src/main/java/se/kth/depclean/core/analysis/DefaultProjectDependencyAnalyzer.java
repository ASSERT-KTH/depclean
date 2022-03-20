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

package se.kth.depclean.core.analysis;

import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import se.kth.depclean.core.analysis.asm.ASMDependencyAnalyzer;
import se.kth.depclean.core.analysis.graph.DefaultCallGraph;

/**
 * This is principal class that perform the dependency analysis in a Maven project.
 */
@Slf4j
public class DefaultProjectDependencyAnalyzer implements ProjectDependencyAnalyzer {

  private final DependencyAnalyzer dependencyAnalyzer = new ASMDependencyAnalyzer();

  /**
   * If true, the project's classes in target/test-classes are not going to be analyzed.
   */
  private final boolean isIgnoredTest;

  /**
   * Ctor.
   */
  public DefaultProjectDependencyAnalyzer(boolean isIgnoredTest) {
    this.isIgnoredTest = isIgnoredTest;
  }

  /**
   * Analyze the dependencies in a project.
   *
   * @param project The Maven project to be analyzed.
   * @return An object with the usedDeclaredArtifacts, usedUndeclaredArtifacts, and unusedDeclaredArtifacts.
   * @throws ProjectDependencyAnalyzerException if the analysis fails.
   * @see <code>ProjectDependencyAnalyzer#analyze(org.apache.invoke.project.MavenProject)</code>
   */
  @Override
  public ProjectDependencyAnalysis analyze(MavenProject project) throws ProjectDependencyAnalyzerException {
    try {
      // a map of [dependency] -> [classes]
      final DeclaredDependencyGraph declaredDependencyGraph =
          new DeclaredDependencyGraph(project.getArtifacts(), project.getDependencyArtifacts());
      final ActualUsedClasses actualUsedClasses = new ActualUsedClasses(declaredDependencyGraph);

      /* ******************** bytecode analysis ********************* */

      // execute the analysis (note that the order of these operations matters!)
      actualUsedClasses.registerClasses(getProjectDependencyClasses(project));
      if (!isIgnoredTest) {
        actualUsedClasses.registerClasses(getProjectTestDependencyClasses(project));
      }
      actualUsedClasses.registerClasses(collectUsedClassesFromProcessors(project));

      /* ******************** usage analysis ********************* */

      // search for the dependencies used by the project
      Set<String> projectClasses = new HashSet<>(DefaultCallGraph.getProjectVertices());
      log.info("# DefaultCallGraph.referencedClassMembers()");
      actualUsedClasses.registerClasses(DefaultCallGraph.referencedClassMembers(projectClasses));

      /* ******************** results as statically used at the bytecode *********************** */
      return new ProjectDependencyAnalysisBuilder(declaredDependencyGraph, actualUsedClasses).analyse();
    } catch (IOException exception) {
      throw new ProjectDependencyAnalyzerException("Cannot analyze dependencies", exception);
    }
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
   *
   * @param project the maven project
   */
  private Iterable<String> collectUsedClassesFromProcessors(MavenProject project) {
    log.info("# collectUsedClassesFromProcessors()");
    return Optional.ofNullable(project.getPlugin("org.bsc.maven:maven-processor-plugin"))
        .map(plugin -> plugin.getExecutionsAsMap().get("process"))
        .map(exec -> (Xpp3Dom) exec.getConfiguration())
        .map(config -> config.getChild("processors"))
        .map(Xpp3Dom::getChildren)
        .map(arr -> Arrays.stream(arr).map(Xpp3Dom::getValue).collect(Collectors.toSet()))
        .orElse(ImmutableSet.of());
  }

  private Iterable<String> getProjectDependencyClasses(MavenProject project) throws IOException {
    // Analyze src classes in the project
    log.info("# getProjectDependencyClasses()");
    return collectDependencyClasses(project.getBuild().getOutputDirectory());
  }

  private Iterable<String> getProjectTestDependencyClasses(MavenProject project) throws IOException {
    // Analyze test classes in the project
    log.info("# getProjectTestDependencyClasses()");
    return collectDependencyClasses(project.getBuild().getTestOutputDirectory());
  }

  private Iterable<String> collectDependencyClasses(String path) throws IOException {
    URL url = new File(path).toURI().toURL();
    return dependencyAnalyzer.analyze(url);
  }
}
