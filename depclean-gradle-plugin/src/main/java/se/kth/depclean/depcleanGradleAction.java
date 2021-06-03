package se.kth.depclean;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.SneakyThrows;
import se.kth.depclean.analysis.DefaultGradleProjectDependencyAnalyzer;
import se.kth.depclean.analysis.GradleProjectDependencyAnalysis;
import se.kth.depclean.core.analysis.ProjectDependencyAnalyzerException;
import se.kth.depclean.util.JarUtils;
import se.kth.depclean.util.MavenInvoker;

public class depcleanGradleAction implements Action<Project> {

    public static final Logger log = LoggerFactory.getLogger(depcleanGradleAction.class);
    private static final String SEPARATOR = "-------------------------------------------------------";

    @SneakyThrows
    @Override
    public void execute(Project project) {

        printString(SEPARATOR);
        String sep = File.separator;
        log.info("Starting DepClean dependency analysis");
        String projectDir = project.getProjectDir().getAbsolutePath() + sep;
        String dependencyDirectoryName = projectDir + "build" + sep + "dependency";
        String libsDirectoryName = projectDir + "build" + sep + "libs";
        String classesDirectoryName = projectDir + "build" + sep + "classes";
//        File buildFile = new File(projectDir + "build.gradle");

        /* Copy direct dependencies locally */
//        writeFile(buildFile);
//        project.setBuildDir(buildFile);
//        try {
//            MavenInvoker.runCommand("gradle copyDependencies");
//        } catch (IOException | InterruptedException e) {
//          log.error("Unable to resolve all the dependencies.");
//          Thread.currentThread().interrupt();
//          return;
//        }



        // TODO remove this workaround later
        if (new File(libsDirectoryName).exists()) {
          try {
            FileUtils.copyDirectory(new File(libsDirectoryName),
                new File(dependencyDirectoryName)
            );
          } catch (IOException | NullPointerException e) {
            log.error("Error copying directory libs to dependency");
          }
        }


        /* Get the size of all the dependencies */
        Map<String, Long> sizeOfDependencies = new HashMap<>();
        // First, add the size of the project, as the sum of all the files in target/classes
        String projectJar = project.getName() + "-" + project.getVersion() + ".jar";

        long projectSize = FileUtils.sizeOf(new File(classesDirectoryName));
        sizeOfDependencies.put(projectJar, projectSize);
        if (Files.exists(Path.of(String.valueOf(Paths.get(
                dependencyDirectoryName))))) {
          Iterator<File> iterator = FileUtils.iterateFiles(
              new File(
                  dependencyDirectoryName), new String[] {"jar"}, true);
          while (iterator.hasNext()) {
            File file = iterator.next();
            sizeOfDependencies.put(file.getName(), FileUtils.sizeOf(file));
          }
        } else {
          log.warn("Dependencies where not copied locally");
        }

        /* Decompress dependencies */
        File dependencyDirectory = new File(dependencyDirectoryName);
        if (dependencyDirectory.exists()) {
            JarUtils.decompressJars(dependencyDirectoryName);
        } else {
            printString("Unable to decompress jars at " + dependencyDirectoryName);
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

//        // TODO Just for debugging purpose, will remove later.
//        debug("used Transitive Artifacts", usedTransitiveArtifacts);
//        debug("used Artifacts", usedDirectArtifacts);
//        debug("Declared Artifacts", unusedDirectArtifacts);
//        debug("all artifacts", unusedTransitiveArtifacts);

        ConfigurationContainer configurationContainer = project.getConfigurations();
        Set<Configuration> configurations = new HashSet<>(configurationContainer);

        List<Dependency> dependencyList = new LinkedList<>();
        Set<String> declaredArtifactsGroupArtifactIds = new HashSet<>();
        for (Configuration configuration : configurations) {
            dependencyList.addAll(configuration.getAllDependencies());
        }
        for (Dependency dep : dependencyList) {
          declaredArtifactsGroupArtifactIds.add("(" +
                  dep.getGroup() + ":" +
                  dep.getName() + ":" +
                  dep.getVersion() + ")");
        }
        // TODO Just for debugging purpose, will remove later.
//        System.out.println("DECLARED ONES");
//        for (String dep : declaredArtifactsGroupArtifactIds) {
//            printString(dep);
//        }

        // --- used dependencies
        Set<String> usedDirectArtifactsCoordinates = new HashSet<>();
        Set<String> usedInheritedArtifactsCoordinates = new HashSet<>();
        Set<String> usedTransitiveArtifactsCoordinates = new HashSet<>();

        for (ResolvedArtifact artifact : usedDirectArtifacts) {
            String artifactGroupArtifactIds = getName(artifact);
            if (declaredArtifactsGroupArtifactIds.contains(artifactGroupArtifactIds)) {
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
            String artifactGroupArtifactIds = getName(artifact);
            usedTransitiveArtifactsCoordinates.add(artifactGroupArtifactIds);
        }

        // --- unused dependencies
        Set<String> unusedDirectArtifactsCoordinates = new HashSet<>();
        Set<String> unusedInheritedArtifactsCoordinates = new HashSet<>();
        Set<String> unusedTransitiveArtifactsCoordinates = new HashSet<>();

        for (ResolvedArtifact artifact : unusedDirectArtifacts) {
            String artifactGroupArtifactIds = getName(artifact);
            if (declaredArtifactsGroupArtifactIds.contains(artifactGroupArtifactIds)) {
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
            String artifactGroupArtifactIds = getName(artifact);
            unusedTransitiveArtifactsCoordinates.add(artifactGroupArtifactIds);
        }

        // Filtering with name cause removeAll function is not working on unusedDirectArtifact set.
        unusedTransitiveArtifactsCoordinates.removeAll(usedDirectArtifactsCoordinates);
        unusedTransitiveArtifactsCoordinates.removeAll(usedTransitiveArtifactsCoordinates);
        unusedTransitiveArtifactsCoordinates.removeAll(unusedDirectArtifactsCoordinates);

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

        // TODO Just for debugging purpose.
        System.out.println(SEPARATOR);
        System.out.println("Debugging size of dependencies".toUpperCase(Locale.ROOT));
        for (Map.Entry<String, Long> entry : sizeOfDependencies.entrySet()) {
            System.out.println("\t" + entry.getKey() + "(" + entry.getValue() + ")");
        }
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
            .get(dependency + ".jar");
        return Objects.requireNonNullElse(size, 0L);
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
        String[] break1 = dependency.split("\\)");
        String[] a = break1[0].split(":");
        String dep = a[1] + "-" + a[2];
        if (sizeOfDependencies.containsKey(dep + ".jar")) {
          return FileUtils.byteCountToDisplaySize(sizeOfDependencies.get(dep + ".jar"));
        } else {
          // The size cannot be obtained.
          return "size unknown";
        }
    }


//    private static void writeFile(File destination) throws IOException {
//        BufferedReader br = new BufferedReader(new FileReader(destination));
//        HashSet<String> destinationContent = new HashSet<>();
//        String destinationLine = br.readLine();
//        while(destinationLine != null) {
//            destinationContent.add(destinationLine);
//            destinationLine = br.readLine();
//        }
//        String contentLine1 = "task copyDependencies(type: Copy) {\n";
//        String contentLine2 = "from configurations.default\n";
//        String contentLine3 = "into 'build/dependency'\n";
//        String contentLine4 = "}\n";
//        String[] content = {contentLine1, contentLine2, contentLine3, contentLine4};
//        for (String line : content) {
//            if (destinationContent.contains(line)) continue;
//            try (BufferedWriter output = new BufferedWriter(new FileWriter(destination, true))) {
//                output.write(line + System.getProperty("line.separator"));
//            }
//        }
//        br.close();
//    }
//    public void debug(String info, Set<ResolvedArtifact> set) {
//        System.out.println(SEPARATOR);
//        System.out.println(info.toUpperCase(Locale.ROOT));
//        System.out.println(SEPARATOR);
//        for (ResolvedArtifact artifact : set) {
//            System.out.println(artifact.getId());
//        }
//        System.out.println(SEPARATOR);
//    }

    public String getName(ResolvedArtifact artifact) {
        String[] artifactGroupArtifactId = artifact.toString().split(" ");
        return artifactGroupArtifactId[1];
    }
}
