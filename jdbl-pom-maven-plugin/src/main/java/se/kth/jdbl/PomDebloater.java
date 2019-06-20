package se.kth.jdbl;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import spoon.MavenLauncher;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PomDebloater {

    public static void main(String[] args) throws IOException, MavenInvocationException, XmlPullParserException {

        String pathToDependencies = "./target/dependency";
        String debloatedPomPath = "./jdbl-pom.xml";
        debloatPom(pathToDependencies, debloatedPomPath);
    }

    public static void debloatPom(String pathToDependencies, String debloatedPomPath)
            throws IOException, XmlPullParserException, MavenInvocationException {
        MavenLauncher launcher = new MavenLauncher("./", MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
        launcher.addProcessor(new CtTypeProcessor());
        launcher.run();

        StringBuilder unusedDependencies = new StringBuilder();

        /* Remove copied dependencies if exist */
        File file = new File(pathToDependencies);
        if (file.exists()) {
            FileUtils.deleteDirectory(new File(pathToDependencies));
        }

        /* Copy direct dependencies locally */
        MavenInvoker.runCommand("mvn dependency:copy-dependencies -DexcludeTransitive");

        /* Get a map of jars and their corresponding types */
        Map<File, List<String>> jarAndTypes = JarUtils.getMapOfJarAndTypes(new File(pathToDependencies));

        /* Get the types used in the project */
        Set<String> unusedJars = getUnusedJars(jarAndTypes, CtTypeProcessor.TypeReferences.types);

        // print potentially unused jars
        System.out.println("\nPotentially unused dependencies: ");
        unusedJars.stream().forEach(System.out::println);

        /* Remove the dependency and check its safety by rebuilding the project */
        Model model = PomManipulator.readModel(new File("./pom.xml"));
        for (String unusedJar : unusedJars) {

            System.out.println("unused jar: " + unusedJar);
            List<Dependency> dependencies = model.getDependencies();

            for (Dependency dependency : dependencies) {
                String dep = dependency.getArtifactId() + "-" + dependency.getVersion() + ".jar";
                if (unusedJar.equals(dep) && (dependency.getScope() == null || !dependency.getScope().equals("runtime"))) {
                    model.removeDependency(dependency);
                    break;
                }
            }

            System.out.println("dependency removed");

            PomManipulator.writePom(Paths.get(debloatedPomPath), model);

            System.out.println("pom written");

            int buildResult = MavenInvoker.invokeMaven("/usr/share/maven", "./pom.xml", "test");
            if (buildResult != 0) {
                System.err.println("Build failed.");
            } else {
                System.out.println("BUILD SUCCESS");
                unusedDependencies.append(unusedJar + "\n");
                model = PomManipulator.readModel(new File(debloatedPomPath));
            }
        }

        FileUtils.deleteDirectory(new File("./spooned"));

        System.out.println("\nRemoved dependencies:");
        System.out.println(unusedDependencies);

        PrintWriter printWriter = new PrintWriter("removed_dependencies.txt");
        System.out.println("writing file...");
        printWriter.print(unusedDependencies.toString());
        printWriter.close();

    }

    private static Set<String> getUnusedJars(Map<File, List<String>> jarAndTypes, HashSet<String> referencedTypes) {
        /* Get jar names that do not have used types */
        Set<String> unusedJars = new HashSet<>();
        for (Map.Entry<File, List<String>> entry : jarAndTypes.entrySet()) {
            String jarFile = entry.getKey().getName();
            for (int i = 0; i < entry.getValue().size(); i++) {
                if (referencedTypes.contains(entry.getValue().get(i))) {
                    // the jar is used
                    break;
                } else if (i == entry.getValue().size() - 1) {
                    // all the classes have been searched
                    unusedJars.add(jarFile);
                }
            }
        }
        return unusedJars;
    }
}
