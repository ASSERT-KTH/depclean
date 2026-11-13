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
import java.util.Collections;
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
    // the compiled classes of the project; the tests are left out when they are ignored
    final boolean analyzeTests = !projectContext.ignoreTests();
    final Set<Path> testClassDirectories =
        analyzeTests ? projectContext.getTestOutputFolders() : Collections.emptySet();

    // every dependency is traversed, whatever its scope: a class of an ignored dependency can be
    // the only path to a class of a dependency that is not ignored
    final List<File> classpath = new ArrayList<>();
    for (Dependency dependency : projectContext.getDependencyGraph().allDependencies()) {
      if (dependency.getFile() != null) {
        classpath.add(dependency.getFile());
      }
    }

    final ClassUsageGraph classUsageGraph =
        classUsageAnalyzer.analyze(
            projectContext.getOutputFolders(),
            testClassDirectories,
            classpath,
            names(projectContext.getExtraClasses()),
            names(projectContext.getExtraTestClasses()));

    final ActualUsedClasses actualUsedClasses = new ActualUsedClasses(projectContext);
    actualUsedClasses.registerClasses(classNames(classUsageGraph.getReachableClasses()));

    // Without tests there is nothing to tell apart from the main code.
    final Set<ClassName> classesUsedByMain =
        analyzeTests
            ? classNames(classUsageGraph.getClassesReachableFromMain())
            : actualUsedClasses.getRegisteredClasses();

    return new ProjectDependencyAnalysisBuilder(
            projectContext, actualUsedClasses, classUsageGraph, classesUsedByMain)
        .analyse();
  }

  private static Set<String> names(Set<ClassName> classes) {
    return classes.stream().map(ClassName::getValue).collect(Collectors.toSet());
  }

  private static Set<ClassName> classNames(Set<String> names) {
    return names.stream().map(ClassName::new).collect(Collectors.toSet());
  }
}
