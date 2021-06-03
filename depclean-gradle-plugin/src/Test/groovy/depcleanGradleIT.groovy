import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import java.util.function.BooleanSupplier

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.gradle.testkit.runner.TaskOutcome.*
import spock.lang.Specification

import static org.junit.jupiter.api.Assertions.assertTrue

class depcleanGradleIT extends Specification {

//    File testProjectDir;
//    private File settingsFile;
//    private File buildFile;
//
//    @BeforeEach
//    void setup() {
//        settingsFile = new File(testProjectDir, "settings.gradle");
//        buildFile = new File(testProjectDir, "build.gradle");
//    }
//
//    @Test
//    void testHelloWorldTask() throws IOException {
//        writeFile(settingsFile, "rootProject.name = 'hello-world'");
//        String buildFileContent = """
//            buildscript{
//                repositories {
//                    mavenLocal()
//                    dependencies{
//                        classpath 'se.kth.castor:depclean-gradle-plugin:1.0-SNAPSHOT'
//                    }
//                }
//            }
//            apply plugin: 'se.kth.castor.depclean-gradle-plugin'
//        """;
//        writeFile(buildFile, buildFileContent);
//
//        BuildResult result = GradleRunner.create()
//            .withProjectDir(testProjectDir)
//            .withArguments("debloat")
//            .build();
//
//        assertEquals(SUCCESS, result.task(":helloWorld").getOutcome());
//    }
//
//    private static void writeFile(File destination, String content) throws IOException {
//        BufferedWriter output = null;
//        try {
//            output = new BufferedWriter(new FileWriter(destination));
//            output.write(content);
//        } finally {
//            if (output != null) {
//                output.close();
//            }
//        }
//    }
    File testProjectDir = new File("resources/all_dependencies_unused")
    File settingsFile
    File buildFile

    def setup() {
        settingsFile = new File(testProjectDir, 'settings.gradle')
        buildFile = new File(testProjectDir, 'build.gradle')
    }
    @Test
    def "all_dependencies_unused"() {
        given:
        def project = ProjectBuilder.builder().withProjectDir(testProjectDir).build()

        when:
        project.plugins.apply("se.kth.castor.depclean-gradle-plugin")

        then:
        project.tasks.findByName("debloat") != null

    }

//    @Test
//    def "success_test"() {
//        given:
//        def project = ProjectBuilder.builder().withProjectDir(testProjectDir).build()
//
//        when:
//        project.plugins.apply("se.kth.castor.depclean-gradle-plugin")
//        BuildResult result = GradleRunner.create()
//                            .withProjectDir(testProjectDir)
//                            .withArguments("debloat")
//                            .build()
//
//        then:
//        assertTrue(result.task("debloat").getOutcome() == SUCCESS)
//
//    }


}
