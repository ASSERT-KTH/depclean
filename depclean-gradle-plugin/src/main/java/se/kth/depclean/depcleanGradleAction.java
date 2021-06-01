package se.kth.depclean;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
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
import org.apache.commons.io.FileUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.tasks.testing.junit.result.TestOutputStore;
import org.gradle.tooling.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.depclean.analysis.DefaultGradleProjectDependencyAnalyzer;
import se.kth.depclean.analysis.GradleProjectDependencyAnalysis;
import se.kth.depclean.core.analysis.ProjectDependencyAnalyzerException;
import se.kth.depclean.util.JarUtils;
import se.kth.depclean.util.MavenInvoker;

public class depcleanGradleAction implements Action<Project> {

    public static final Logger log = LoggerFactory.getLogger(depcleanGradleAction.class);
    private static final String SEPARATOR = "-------------------------------------------------------";
    public static final String DIRECTORY_TO_COPY_DEPENDENCIES = "dependency";

    @Override
    public void execute(Project project) {

        printString(SEPARATOR);
        log.info("Starting DepClean dependency analysis");

        File buildFile = new File(project.getProjectDir().getAbsolutePath() + File.separator + "build.gradle");

        project.setBuildDir(buildFile);

        /* Copy direct dependencies locally */
        try {
          MavenInvoker.runCommand("gradle copyDependencies");
        } catch (IOException | InterruptedException e) {
          log.error("Unable to resolve all the dependencies.");
          Thread.currentThread().interrupt();
          return;
        }

        // TODO remove this workaround later
        if (new File(project.getBuildDir().getAbsolutePath() + File.separator + "libs").exists()) {
          try {
            FileUtils.copyDirectory(new File(project.getBuildDir().getAbsolutePath() + File.separator + "libs"),
                new File(project.getBuildDir().getAbsolutePath() + File.separator + DIRECTORY_TO_COPY_DEPENDENCIES)
            );
          } catch (IOException | NullPointerException e) {
            log.error("Error copying directory libs to dependency");
          }
        }


        /* Get the size of all the dependencies */
        Map<String, Long> sizeOfDependencies = new HashMap<>();
        // First, add the size of the project, as the sum of all the files in target/classes
        String projectJar = project.getDisplayName() + "-" + project.getVersion() + ".jar";

        long projectSize = FileUtils.sizeOf(new File("build" + File.separator + "classes"));
        sizeOfDependencies.put(projectJar, projectSize);
        if (Files.exists(Path.of(String.valueOf(Paths.get(
                project.getBuildDir().getAbsolutePath() + File.separator + DIRECTORY_TO_COPY_DEPENDENCIES))))) {
          Iterator<File> iterator = FileUtils.iterateFiles(
              new File(
                  project.getBuildDir() + File.separator
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
        project.getBuildDir() + File.separator + DIRECTORY_TO_COPY_DEPENDENCIES;
        File dependencyDirectory = new File(dependencyDirectoryName);
        if (dependencyDirectory.exists()) {
            JarUtils.decompressJars(dependencyDirectoryName);
        }

        /* Analyze dependencies usage status */
        GradleProjectDependencyAnalysis projectDependencyAnalysis = null;
        DefaultGradleProjectDependencyAnalyzer dependencyAnalyzer = new DefaultGradleProjectDependencyAnalyzer(false);
        try {
          projectDependencyAnalysis = dependencyAnalyzer.analyze(project);
        } catch (ProjectDependencyAnalyzerException e) {
          log.error("Unable to analyze dependencies.");
        }

        assert projectDependencyAnalysis != null;
        Set<ResolvedArtifact> usedTransitiveArtifacts = projectDependencyAnalysis.getUsedUndeclaredArtifacts();
        Set<ResolvedArtifact> usedDirectArtifacts = projectDependencyAnalysis.getUsedDeclaredArtifacts();
        Set<ResolvedArtifact> unusedDirectArtifacts = projectDependencyAnalysis.getUnusedDeclaredArtifacts();
        Set<ResolvedArtifact> unusedTransitiveArtifacts = projectDependencyAnalysis.getAllArtifacts();

        unusedTransitiveArtifacts.removeAll(usedDirectArtifacts);
        unusedTransitiveArtifacts.removeAll(usedTransitiveArtifacts);
        unusedTransitiveArtifacts.removeAll(unusedDirectArtifacts);

        ConfigurationContainer configurationContainer = project.getConfigurations();
        Set<Configuration> configurations = new HashSet<>(configurationContainer);

        List<Dependency> dependencyList = new LinkedList<>();
        Set<String> declaredArtifactsGroupArtifactIds = new HashSet<>();
        for (Configuration configuration : configurations) {
            dependencyList.addAll(configuration.getAllDependencies());
        }
        for (Dependency dep : dependencyList) {
          declaredArtifactsGroupArtifactIds.add(dep.getGroup() + ":" + dep.getName());
        }

        // --- used dependencies
        Set<String> usedDirectArtifactsCoordinates = new HashSet<>();
        Set<String> usedInheritedArtifactsCoordinates = new HashSet<>();
        Set<String> usedTransitiveArtifactsCoordinates = new HashSet<>();

        for (ResolvedArtifact artifact : usedDirectArtifacts) {
          String artifactGroupArtifactId = artifact.toString().split(":")[0] + ":" + artifact.getId();
          String artifactGroupArtifactIds =
            artifactGroupArtifactId + ":" + artifact.toString().split(":")[3] + ":" + artifact.toString()
                  .split(":")[4];
          if (declaredArtifactsGroupArtifactIds.contains(artifactGroupArtifactId)) {
            // the artifact is declared in the build file
            usedDirectArtifactsCoordinates.add(artifactGroupArtifactIds);
          } else {
              // the artifact is inherited
              usedInheritedArtifactsCoordinates.add(artifactGroupArtifactIds);
          }
        }

        // TODO Fix: The used transitive dependencies induced by inherited dependencies should be considered
        //  as used inherited
        for (ResolvedArtifact artifact : usedTransitiveArtifacts) {
          String artifactGroupArtifactId = artifact.toString().split(":")[0] + ":" + artifact.getId();
          String artifactGroupArtifactIds =
              artifactGroupArtifactId + ":" + artifact.toString().split(":")[3] + ":" + artifact.toString()
                  .split(":")[4];
          usedTransitiveArtifactsCoordinates.add(artifactGroupArtifactIds);
        }

        // --- unused dependencies
        Set<String> unusedDirectArtifactsCoordinates = new HashSet<>();
        Set<String> unusedInheritedArtifactsCoordinates = new HashSet<>();
        Set<String> unusedTransitiveArtifactsCoordinates = new HashSet<>();

        for (ResolvedArtifact artifact : unusedDirectArtifacts) {
          String artifactGroupArtifactId = artifact.toString().split(":")[0] + ":" + artifact.getId();
          String artifactGroupArtifactIds =
              artifactGroupArtifactId + ":" + artifact.toString().split(":")[3] + ":" + artifact.toString()
                  .split(":")[4];
          if (declaredArtifactsGroupArtifactIds.contains(artifactGroupArtifactId)) {
              // artifact is declared in build file
              unusedDirectArtifactsCoordinates.add(artifactGroupArtifactIds);
          } else {
              // the artifact is inherited
              unusedInheritedArtifactsCoordinates.add(artifactGroupArtifactIds);
          }
        }

        // TODO Fix: The unused transitive dependencies induced by inherited dependencies should be considered as
        //  unused inherited
        for (ResolvedArtifact artifact : unusedTransitiveArtifacts) {
          String artifactGroupArtifactId = artifact.toString().split(":")[0] + ":" + artifact.getId();
          String artifactGroupArtifactIds =
              artifactGroupArtifactId + ":" + artifact.toString().split(":")[3] + ":" + artifact.toString()
                  .split(":")[4];
          unusedTransitiveArtifactsCoordinates.add(artifactGroupArtifactIds);
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
    }

    /**
     * Util function to print the information of the analyzed artifacts.
     *
     * @param info               The usage status (used or unused) and type (direct, transitive, inherited) of artifacts.
     * @param sizeOfDependencies The size of the JAR file of the artifact.
     * @param dependencies       The GAV of the artifact.
     */
    private void printInfoOfDependencies(final String info, final Map<String,
                    Long> sizeOfDependencies,
          final Set<String> dependencies) {
        printString(info.toUpperCase() + " [" + dependencies.size() + "]" + ": ");
        printDependencies(sizeOfDependencies, dependencies);
    }


    private void printString(final String string) {
        System.out.println(string); //NOSONAR avoid a warning of non-used logger
    }

    /**
     * Print the status of the dependencies to the standard output. The format is: "[coordinates][scope] [(size)]"
     *
     * @param sizeOfDependencies A map with the size of the dependencies.
     * @param dependencies       The set dependencies to print.
     */
    private void printDependencies(final Map<String, Long> sizeOfDependencies, final Set<String> dependencies) {
        dependencies
          .stream()
          .sorted(Comparator.comparing(o -> getSizeOfDependency(sizeOfDependencies, o)))
          .collect(Collectors.toCollection(LinkedList::new))
          .descendingIterator()
          .forEachRemaining(s -> printString("\t" + s + " (" + getSize(s, sizeOfDependencies) + ")"));
    }

    /**
     * Utility method to obtain the size of a dependency from a map of dependency -> size. If the size of the dependency
     * cannot be obtained form the map (no key with the name of the dependency exists), then it returns 0.
     *
     * @param sizeOfDependencies A map of dependency -> size.
     * @param dependency         The coordinates of a dependency.
     * @return The size of the dependency if its name is a key in the map, otherwise it returns 0.
     */
    private Long getSizeOfDependency(final Map<String, Long> sizeOfDependencies, final String dependency) {
        Long size = sizeOfDependencies
            .get(dependency.split(":")[1] + "-" + dependency.split(":")[2] + ".jar");
        if (size != null) {
          return size;
        } else {
          // The name of the dependency does not match with the name of the download jar, so we keep assume the size
          // cannot be obtained and return 0.
          return 0L;
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
    private String getSize(final String dependency, final Map<String, Long> sizeOfDependencies) {
        String dep = dependency.split(":")[1] + "-" + dependency.split(":")[2] + ".jar";
        if (sizeOfDependencies.containsKey(dep)) {
          return FileUtils.byteCountToDisplaySize(sizeOfDependencies.get(dep));
        } else {
          // The size cannot be obtained.
          return "size unknown";
        }
    }

}
