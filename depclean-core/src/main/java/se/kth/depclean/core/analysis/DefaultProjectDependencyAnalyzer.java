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

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import se.kth.depclean.core.analysis.asm.ASMDependencyAnalyzer;
import se.kth.depclean.core.analysis.graph.DefaultCallGraph;
import se.kth.depclean.core.analysis.model.ProjectDependencyAnalysis;
import se.kth.depclean.core.model.ClassName;
import se.kth.depclean.core.model.ProjectContext;

/**
 * This is principal class that perform the dependency analysis in a Maven project.
 */
@Slf4j
public class DefaultProjectDependencyAnalyzer {

  private final DependencyAnalyzer dependencyAnalyzer = new ASMDependencyAnalyzer();
  private final ProjectContext projectContext;

  /**
   * Ctor.
   */
  public DefaultProjectDependencyAnalyzer(ProjectContext projectContext) {
    this.projectContext = projectContext;
  }

  /**
   * Analyze the dependencies in a project.
   *
   * @return An object representing the analysis result.
   * @throws ProjectDependencyAnalyzerException if the analysis fails.
   */
  public ProjectDependencyAnalysis analyze() throws ProjectDependencyAnalyzerException {
    try {
      // a map of [dependency] -> [classes]
      final ActualUsedClasses actualUsedClasses = new ActualUsedClasses(projectContext);

      /* ******************** bytecode analysis ********************* */

      // execute the analysis (note that the order of these operations matters!)
      actualUsedClasses.registerClasses(getProjectDependencyClasses(projectContext.getOutputFolder()));
      if (!projectContext.ignoreTests()) {
        log.trace("Parsing test folder");
        actualUsedClasses.registerClasses(getProjectTestDependencyClasses(projectContext.getTestOutputFolder()));
      }
      actualUsedClasses.registerClasses(projectContext.getExtraClasses());

      /* ******************** usage analysis ********************* */

      // search for the dependencies used by the project
      Set<String> projectClasses = new HashSet<>(DefaultCallGraph.getProjectVertices());
      log.trace("# DefaultCallGraph.referencedClassMembers()");
      actualUsedClasses.registerClasses(getReferencedClassMembers(projectClasses));

      /* ******************** results as statically used at the bytecode *********************** */
      return new ProjectDependencyAnalysisBuilder(projectContext, actualUsedClasses).analyse();
    } catch (IOException exception) {
      throw new ProjectDependencyAnalyzerException("Cannot analyze dependencies", exception);
    }
  }

  private Iterable<ClassName> getProjectDependencyClasses(Path outputFolder) throws IOException {
    // Analyze src classes in the project
    log.trace("# getProjectDependencyClasses()");
    return collectDependencyClasses(outputFolder);
  }

  private Iterable<ClassName> getProjectTestDependencyClasses(Path testOutputFolder) throws IOException {
    // Analyze test classes in the project
    log.trace("# getProjectTestDependencyClasses()");
    return collectDependencyClasses(testOutputFolder);
  }

  private Iterable<ClassName> collectDependencyClasses(Path path) throws IOException {
    return dependencyAnalyzer.analyze(path.toUri().toURL()).stream()
        .map(ClassName::new)
        .collect(Collectors.toSet());
  }

  private Iterable<ClassName> getReferencedClassMembers(Set<String> projectClasses) {
    return DefaultCallGraph.referencedClassMembers(projectClasses).stream()
        .map(ClassName::new)
        .collect(Collectors.toSet());
  }
}
