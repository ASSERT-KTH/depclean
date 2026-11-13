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

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import se.kth.depclean.core.analysis.graph.ClassUsageGraph;
import se.kth.depclean.core.analysis.model.ProjectDependencyAnalysis;
import se.kth.depclean.core.model.ClassName;
import se.kth.depclean.core.model.Dependency;
import se.kth.depclean.core.model.ProjectContext;

/** This is principal class that perform the dependency analysis in a Maven project. */
public class DefaultProjectDependencyAnalyzer {

  private final ClassUsageAnalyzer classUsageAnalyzer = new ClassUsageAnalyzer();

  /**
   * Analyze the dependencies in a project.
   *
   * @param projectContext The project's context
   * @return An object with the usedDeclaredArtifacts, usedUndeclaredArtifacts, and
   *     unusedDeclaredArtifacts.
   */
  public ProjectDependencyAnalysis analyze(final ProjectContext projectContext) {
    // the compiled classes of the project, tests included unless they are ignored
    final Set<Path> projectClassDirectories =
        new LinkedHashSet<>(projectContext.getOutputFolders());
    if (!projectContext.ignoreTests()) {
      projectClassDirectories.addAll(projectContext.getTestOutputFolders());
    }

    // every dependency is traversed, whatever its scope: a class of an ignored dependency can be
    // the only path to a class of a dependency that is not ignored
    final List<File> classpath = new ArrayList<>();
    for (Dependency dependency : projectContext.getDependencyGraph().allDependencies()) {
      if (dependency.getFile() != null) {
        classpath.add(dependency.getFile());
      }
    }

    final ClassUsageGraph classUsageGraph =
        classUsageAnalyzer.analyze(projectClassDirectories, classpath);

    final ActualUsedClasses actualUsedClasses = new ActualUsedClasses(projectContext);
    // classes found by the static analysis of the sources and of the resources
    actualUsedClasses.registerClasses(projectContext.getExtraClasses());
    // classes reachable from the bytecode of the project
    actualUsedClasses.registerClasses(
        classUsageGraph.getReachableClasses().stream()
            .map(ClassName::new)
            .collect(Collectors.toSet()));

    return new ProjectDependencyAnalysisBuilder(projectContext, actualUsedClasses, classUsageGraph)
        .analyse();
  }
}
