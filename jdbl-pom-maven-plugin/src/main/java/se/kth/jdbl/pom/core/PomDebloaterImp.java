package se.kth.jdbl.pom.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PomDebloaterImp {

    //--------------------------------/
    //-------- CLASS FIELD/S --------/
    //------------------------------/

    private static final Logger LOGGER = LogManager.getLogger(PomDebloaterImp.class.getName());

    //--------------------------------/
    //------- PUBLIC METHOD/S -------/
    //------------------------------/
//
//    public void debloatPom(String pathToDependencies, String pathToDebloatedPom) throws IOException, XmlPullParserException, MavenInvocationException {
//
//        // launch invoke analysis
//
//        MavenRepositorySystem mavenRepositorySystem = new MavenRepositorySystem();
//
//
//        StringBuilder unusedDependencies = new StringBuilder();
//
//        *//* Remove copied dependencies if exist *//*
//        File file = new File(pathToDependencies);
//        if (file.exists()) {
//            FileUtils.deleteDirectory(new File(pathToDependencies));
//        }
//
//        *//* Copy direct dependencies locally *//*
//        MavenInvoker.runCommand("mvn dependency:copy-dependencies -DexcludeTransitive");
//
//        *//* Get a map of jars and their corresponding types *//*
//        Map<File, List<String>> jarAndTypes = JarUtils.getMapOfJarAndTypes(new File(pathToDependencies));
//
//        *//* Get the types used in the project *//*
////        Set<String> unusedJars = getUnusedJars(jarAndTypes, CtTypeProcessor.TypeReferences.types);
//
//        // print potentially unused jars
//        System.out.println("\nPotentially unused dependencies: ");
////        unusedJars.stream().forEach(System.out::println);
//
//        *//* Remove the dependency and check its safety by rebuilding the project *//*
//        Model model = PomManipulator.readModel(new File("./pom.xml"));
//        for (String unusedJar : unusedJars) {
//
//            System.out.println("unused jar: " + unusedJar);
//            List<Dependency> dependencies = model.getDependencies();
//
//            for (Dependency dependency : dependencies) {
//                String dep = dependency.getArtifactId() + "-" + dependency.getVersion() + ".jar";
//                if (unusedJar.equals(dep) && (dependency.getScope() == null || !dependency.getScope().equals("runtime"))) {
//                    model.removeDependency(dependency);
//                    break;
//                }
//            }
//
//            System.out.println("dependency removed");
//
//            PomManipulator.writePom(Paths.get(pathToDebloatedPom), model);
//
//            System.out.println("core written");
//
//            int buildResult = MavenInvoker.invokeMaven("/usr/share/invoke", "./pom.xml", "test");
//            if (buildResult != 0) {
//                System.err.println("Build failed.");
//            } else {
//                System.out.println("BUILD SUCCESS");
//                unusedDependencies.append(unusedJar + "\n");
//                model = PomManipulator.readModel(new File(pathToDebloatedPom));
//            }
//        }
//
//        FileUtils.deleteDirectory(new File("./spooned"));
//
//        System.out.println("\nRemoved dependencies:");
//        System.out.println(unusedDependencies);
//
//        PrintWriter printWriter = new PrintWriter("removed_dependencies.txt");
//        System.out.println("writing file...");
//        printWriter.print(unusedDependencies.toString());
//        printWriter.close();
//
//    }
//
//    //--------------------------------/
//    //------ PRIVATE METHOD/S -------/
//    //------------------------------/
//
//    private static Set<String> getUnusedJars(Map<File, List<String>> jarAndTypes, HashSet<String> referencedTypes) {
//        *//* Get jar names that do not have used types *//*
//        Set<String> unusedJars = new HashSet<>();
//        for (Map.Entry<File, List<String>> entry : jarAndTypes.entrySet()) {
//            String jarFile = entry.getKey().getName();
//            for (int i = 0; i < entry.getValue().size(); i++) {
//                if (referencedTypes.contains(entry.getValue().get(i))) {
//                    // the jar is used
//                    break;
//                } else if (i == entry.getValue().size() - 1) {
//                    // all the classes have been searched
//                    unusedJars.add(jarFile);
//                }
//            }
//        }
//        return unusedJars;
//    }
}
