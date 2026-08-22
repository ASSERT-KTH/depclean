package se.kth.depclean.utils

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

class ClassesDirectoryFinderSpec extends Specification {

    @TempDir
    File projectDir

    private Project buildProject(boolean withJavaPlugin = false) {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        if (withJavaPlugin) {
            project.plugins.apply("java")
        }
        return project
    }

    private File mkdirs(String... segments) {
        File dir = new File(projectDir, segments.join(File.separator))
        assert dir.mkdirs() || dir.isDirectory()
        return dir
    }

    def "returns an empty set when no class directories exist"() {
        given:
        Project project = buildProject()

        expect:
        ClassesDirectoryFinder.findClassesDirectories(project, false).isEmpty()
    }

    def "returns an empty set when build/classes does not exist without failing"() {
        given: "a java project that was never compiled (issue #218 crash scenario)"
        Project project = buildProject(true)

        expect:
        ClassesDirectoryFinder.findClassesDirectories(project, false).isEmpty()
    }

    def "finds main and test classes in the conventional JVM layout"() {
        given:
        Project project = buildProject(true)
        File mainClasses = mkdirs("build", "classes", "java", "main")
        File testClasses = mkdirs("build", "classes", "java", "test")

        when:
        Set<File> result = ClassesDirectoryFinder.findClassesDirectories(project, false)

        then:
        result.contains(mainClasses)
        result.contains(testClasses)
    }

    def "excludes test source sets when tests are ignored"() {
        given:
        Project project = buildProject(true)
        File mainClasses = mkdirs("build", "classes", "java", "main")
        File testClasses = mkdirs("build", "classes", "java", "test")

        when:
        Set<File> result = ClassesDirectoryFinder.findClassesDirectories(project, true)

        then:
        result.contains(mainClasses)
        !result.contains(testClasses)
    }

    def "finds groovy and kotlin classes in the conventional layout"() {
        given:
        Project project = buildProject()
        File groovyClasses = mkdirs("build", "classes", "groovy", "main")
        File kotlinClasses = mkdirs("build", "classes", "kotlin", "main")

        when:
        Set<File> result = ClassesDirectoryFinder.findClassesDirectories(project, false)

        then:
        result.containsAll([groovyClasses, kotlinClasses])
    }

    def "finds custom source set output directories"() {
        given:
        Project project = buildProject(true)
        project.sourceSets.create("integration")
        File integrationClasses = mkdirs("build", "classes", "java", "integration")

        when:
        Set<File> result = ClassesDirectoryFinder.findClassesDirectories(project, false)

        then:
        result.contains(integrationClasses)
    }

    def "finds Android javac classes for multiple product flavors"() {
        given: "the Android layout reported in issue #218"
        Project project = buildProject()
        File devDebug = mkdirs("build", "intermediates", "javac", "devDebug", "classes")
        File prodRelease = mkdirs("build", "intermediates", "javac", "prodRelease", "classes")

        when:
        Set<File> result = ClassesDirectoryFinder.findClassesDirectories(project, false)

        then:
        result.containsAll([devDebug, prodRelease])
    }

    def "excludes Android test variants when tests are ignored"() {
        given:
        Project project = buildProject()
        File devDebug = mkdirs("build", "intermediates", "javac", "devDebug", "classes")
        File unitTest = mkdirs("build", "intermediates", "javac", "devDebugUnitTest", "classes")
        File androidTest = mkdirs("build", "intermediates", "javac", "devDebugAndroidTest", "classes")

        when:
        Set<File> result = ClassesDirectoryFinder.findClassesDirectories(project, true)

        then:
        result.contains(devDebug)
        !result.contains(unitTest)
        !result.contains(androidTest)
    }

    def "includes Android test variants when tests are not ignored"() {
        given:
        Project project = buildProject()
        File unitTest = mkdirs("build", "intermediates", "javac", "devDebugUnitTest", "classes")

        expect:
        ClassesDirectoryFinder.findClassesDirectories(project, false).contains(unitTest)
    }

    def "finds Android kotlin classes per variant"() {
        given:
        Project project = buildProject()
        File debug = mkdirs("build", "tmp", "kotlin-classes", "debug")
        File releaseTest = mkdirs("build", "tmp", "kotlin-classes", "debugUnitTest")

        when:
        Set<File> resultWithTests = ClassesDirectoryFinder.findClassesDirectories(project, false)
        Set<File> resultWithoutTests = ClassesDirectoryFinder.findClassesDirectories(project, true)

        then:
        resultWithTests.containsAll([debug, releaseTest])
        resultWithoutTests.contains(debug)
        !resultWithoutTests.contains(releaseTest)
    }

    def "does not duplicate directories reported by both source sets and layout scan"() {
        given:
        Project project = buildProject(true)
        File mainClasses = mkdirs("build", "classes", "java", "main")

        when:
        Set<File> result = ClassesDirectoryFinder.findClassesDirectories(project, false)

        then:
        result.count { it == mainClasses } == 1
    }
}
