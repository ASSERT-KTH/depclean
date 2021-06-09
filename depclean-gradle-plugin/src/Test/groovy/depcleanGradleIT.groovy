import org.apache.maven.BuildFailureException
import org.gradle.api.internal.tasks.TaskExecuterResult
import org.gradle.api.tasks.TaskOutputs
import org.gradle.internal.impldep.org.apache.maven.settings.building.SettingsBuilder
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.experimental.results.PrintableResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.Result
import java.util.function.BooleanSupplier
import java.util.logging.Logger
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.gradle.testkit.runner.TaskOutcome.*
import spock.lang.Specification
import static org.junit.jupiter.api.Assertions.assertTrue

class depcleanGradleIT extends Specification {

    File testProjectDir = new File("src/Test/resources/all_dependencies_unused")

    @Test
    def "debloatTaskIsFormed"() {
        given:
        def project = ProjectBuilder.builder().withProjectDir(testProjectDir).build()

        when:
        project.plugins.apply("se.kth.castor.depclean-gradle-plugin")

        then:
        project.tasks.findByName("debloat") != null
    }

    File emptyProjectFile = new File("src/Test/resources/empty_project")

    @Test
    def "pluginRunsOnEmptyProject"() {
        given:
        def project = ProjectBuilder.builder().withProjectDir(emptyProjectFile).build()

        when:
        project.plugins.apply("se.kth.castor.depclean-gradle-plugin")

        then:
        try {
            BuildResult result =  GradleRunner.create()
                .withProjectDir(emptyProjectFile)
                .withArguments("debloat")
                .buildAndFail()
        } catch (Exception e) {
            assertEquals(e, BuildFailureException)
        }
    }

//    File allDependenciesUnused = new File("src/Test/resources/all_dependencies_unused")
//    @Test
//    def "all_dependencies_unused"() {
//        given:
//        def project = ProjectBuilder.builder().withProjectDir(allDependenciesUnused).build()
//
//        when:
//        project.plugins.apply("se.kth.castor.depclean-gradle-plugin")
//        BuildResult result = GradleRunner.create()
//            .withProjectDir(allDependenciesUnused)
//            .withArguments("copyDependenciesLocally")
//            .withArguments("debloat")
//            .build()
//
//        then:
//        File Dependency = new File(project.getProjectDir().getAbsolutePath() + File.separator + "build" + File.separator + "Dependency")
//        assertTrue(Dependency.exists())
//        println(result.task(":debloat").getOutcome().toString())
//        assertEquals(SUCCESS, result.task(":debloat").getOutcome())
//        result.output.stripIndent().trim().contains("-------------------------------------------------------".stripIndent().trim())
//        result.output.stripIndent().trim().contains("D E P C L E A N   A N A L Y S I S   R E S U L T S".stripIndent().trim())
//        result.output.stripIndent().trim().contains("-------------------------------------------------------".stripIndent().trim())
//        result.output.stripIndent().trim().contains("USED DIRECT DEPENDENCIES [0]:".stripIndent().trim())
//        result.output.stripIndent().trim().contains("USED INHERITED DEPENDENCIES [0]:".stripIndent().trim())
//        result.output.stripIndent().trim().contains("USED TRANSITIVE DEPENDENCIES [0]:".stripIndent().trim())
//        result.output.stripIndent().trim().contains("POTENTIALLY UNUSED DIRECT DEPENDENCIES [1]:".stripIndent().trim())
//        result.output.stripIndent().trim().contains("\tcom.fasterxml.jackson.core:jackson-databind:2.12.2 (1 MB)".stripIndent().trim())
//        result.output.stripIndent().trim().contains("POTENTIALLY UNUSED INHERITED DEPENDENCIES [0]:".stripIndent().trim())
//        result.output.stripIndent().trim().contains("POTENTIALLY UNUSED TRANSITIVE DEPENDENCIES [2]:".stripIndent().trim())
//        result.output.stripIndent().trim().contains("\tcom.fasterxml.jackson.core:jackson-annotations:2.12.2 (73 KB)".stripIndent().trim())
//        result.output.stripIndent().trim().contains("\tcom.fasterxml.jackson.core:jackson-core:2.12.2 (356 KB)".stripIndent().trim())
//    }

    // D E P C L E A N   A N A L Y S I S   R E S U L T S

//-------------------------------------------------------

//USED DIRECT DEPENDENCIES [0]:

//USED INHERITED DEPENDENCIES [0]:

//USED TRANSITIVE DEPENDENCIES [0]:

//POTENTIALLY UNUSED DIRECT DEPENDENCIES [1]:

//	com.fasterxml.jackson.core:jackson-databind:2.12.2 (1 MB)

//POTENTIALLY UNUSED INHERITED DEPENDENCIES [0]:

//POTENTIALLY UNUSED TRANSITIVE DEPENDENCIES [2]:

//	com.fasterxml.jackson.core:jackson-annotations:2.12.2 (73 KB)

//	com.fasterxml.jackson.core:jackson-core:2.12.2 (356 KB)

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
//    void debloatTaskIsFormed() throws IOException {
//
//        BuildResult result = GradleRunner.create()
//            .withProjectDir(testProjectDir)
//            .withArguments("debloat")
//            .build();
//
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
//    File testProjectDir = new File("resources/all_dependencies_unused")
//    File settingsFile
//    File buildFile
//
//    def setup() {
//        settingsFile = new File(testProjectDir, 'settings.gradle')
//        buildFile = new File(testProjectDir, 'build.gradle')
//    }

//    @Test
//    def "success_test"() {
//
//        given:
//        settingsFile << """
///*
// * This file was generated by the Gradle 'init' task.
// */
//pluginManagement {
//    repositories {
//        maven {
//            url = '/castor/depclean/depclean-gradle-plugin/build/libs'
//        }
//    }
//}
//rootProject.name = 'foobar'
//"""
//        buildFile << """
//buildscript{
//  repositories {
//      mavenLocal()
//
//      dependencies{
//        classpath 'se.kth.castor:depclean-gradle-plugin:1.0-SNAPSHOT'
//      }
//  }
//}
//
//plugins {
//    id 'java'
//    id 'maven-publish'
//}
//apply plugin: 'se.kth.castor.depclean-gradle-plugin'
//
//repositories {
//    mavenLocal()
//    jcenter()
//    mavenCentral()
//}
//
//dependencies {
//    implementation 'com.fasterxml.jackson.core:jackson-databind:2.12.2'
//}
//
//task copyDependencies(type: Copy) {
//  from configurations.default
//  into 'build/dependency'
//}
//
//group = 'org.foo.bar'
//version = '1.0.0-SNAPSHOT'
//description = 'foobar'
//java.sourceCompatibility = JavaVersion.VERSION_1_8
//
//
//tasks.withType(JavaCompile) {
//    options.encoding = 'UTF-8'
//}
//"""
//        when:
//        def result = GradleRunner.create()
//                .withProjectDir(testProjectDir)
//                .withArguments('debloat')
//                .build()
//
//
//        then:
//        println(result.getOutput())
//
//    }

//    allDependenciesUnused
//-------------------------------------------------------
// D E P C L E A N   A N A L Y S I S   R E S U L T S
//-------------------------------------------------------
//USED DIRECT DEPENDENCIES [0]:
//USED INHERITED DEPENDENCIES [0]:
//USED TRANSITIVE DEPENDENCIES [0]:
//POTENTIALLY UNUSED DIRECT DEPENDENCIES [1]:
//	com.fasterxml.jackson.core:jackson-databind:2.12.2 (1 MB)
//POTENTIALLY UNUSED INHERITED DEPENDENCIES [0]:
//POTENTIALLY UNUSED TRANSITIVE DEPENDENCIES [2]:
//	com.fasterxml.jackson.core:jackson-annotations:2.12.2 (73 KB)
//	com.fasterxml.jackson.core:jackson-core:2.12.2 (356 KB)


//    allDependenciesUsed
//-------------------------------------------------------
// D E P C L E A N   A N A L Y S I S   R E S U L T S
//-------------------------------------------------------
//USED DIRECT DEPENDENCIES [1]:
//	com.fasterxml.jackson.core:jackson-databind:2.12.2 (1 MB)
//USED INHERITED DEPENDENCIES [0]:
//USED TRANSITIVE DEPENDENCIES [2]:
//	com.fasterxml.jackson.core:jackson-core:2.12.2 (356 KB)
//	com.fasterxml.jackson.core:jackson-annotations:2.12.2 (73 KB)
//POTENTIALLY UNUSED DIRECT DEPENDENCIES [0]:
//POTENTIALLY UNUSED INHERITED DEPENDENCIES [0]:
//POTENTIALLY UNUSED TRANSITIVE DEPENDENCIES [0]:

//    Processor used.
//    -------------------------------------------------------
// D E P C L E A N   A N A L Y S I S   R E S U L T S
//-------------------------------------------------------
//USED DIRECT DEPENDENCIES [0]:
//USED INHERITED DEPENDENCIES [0]:
//USED TRANSITIVE DEPENDENCIES [1]:
//	com.fasterxml.jackson.core:jackson-core:2.12.2 (356 KB)
//POTENTIALLY UNUSED DIRECT DEPENDENCIES [2]:
//	org.mapstruct:mapstruct-processor:1.4.2.Final (size unknown)
//	com.fasterxml.jackson.core:jackson-databind:2.12.2 (1 MB)
//POTENTIALLY UNUSED INHERITED DEPENDENCIES [0]:
//POTENTIALLY UNUSED TRANSITIVE DEPENDENCIES [1]:
//	com.fasterxml.jackson.core:jackson-annotations:2.12.2 (73 KB)
}