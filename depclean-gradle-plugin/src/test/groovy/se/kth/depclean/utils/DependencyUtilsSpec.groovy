package se.kth.depclean.utils

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

class DependencyUtilsSpec extends Specification {

    @TempDir
    File projectDir

    private final DependencyUtils utils = new DependencyUtils()

    private Project buildProject() {
        ProjectBuilder.builder().withProjectDir(projectDir).build()
    }

    private Set<String> selfDeclaredModules(Project project) {
        new DependencyUtils().getSelfDeclaredModules(project.configurations as Set)
    }

    def "returns nothing when the project declares no dependency"() {
        given:
        Project project = buildProject()
        project.configurations.create('compile')

        expect:
        selfDeclaredModules(project).isEmpty()
    }

    def "collects the declared dependencies of a declarable configuration"() {
        given:
        Project project = buildProject()
        project.configurations.create('compile')
        project.dependencies.add('compile', 'com.example:lib:1.0.0')
        project.dependencies.add('compile', 'com.other:other-lib:2.0.0')

        expect:
        selfDeclaredModules(project) == ['com.example:lib', 'com.other:other-lib'] as Set
    }

    def "ignores configurations the build cannot declare dependencies in"() {
        given: 'a resolvable configuration only holds what other configurations contribute'
        Project project = buildProject()
        project.configurations.create('compile')
        project.dependencies.add('compile', 'com.example:lib:1.0.0')
        project.configurations.create('runtimeClasspath')

        expect:
        selfDeclaredModules(project) == ['com.example:lib'] as Set
    }

    def "prefers modern resolvable classpaths"() {
        given:
        Project project = buildProject()
        Configuration compileClasspath = project.configurations.create('compileClasspath')
        compileClasspath.canBeResolved = true

        expect:
        new DependencyUtils().getResolvableConfigurations(project) == [compileClasspath] as Set
    }

    def "uses legacy resolvable configurations when modern classpaths are absent"() {
        given:
        Project project = buildProject()
        Configuration compile = project.configurations.create('compile')
        compile.canBeResolved = true

        expect:
        new DependencyUtils().getResolvableConfigurations(project) == [compile] as Set
    }

    def "does not attempt to resolve excluded configurations"() {
        given:
        Configuration runtimeOnly = Mock()
        runtimeOnly.name >> 'runtimeOnly'
        runtimeOnly.canBeResolved >> true

        when:
        Set<ResolvedDependency> dependencies = utils.getAllDependencies([runtimeOnly] as Set)

        then:
        dependencies.isEmpty()
        0 * runtimeOnly.getResolvedConfiguration()
    }

    def "records each artifact's resolved configuration"() {
        given:
        ResolvedArtifact artifact = Mock()
        ResolvedDependency dependency = Mock()
        dependency.getModuleArtifacts() >> ([artifact] as Set)
        dependency.getConfiguration() >> 'runtimeClasspath'

        when:
        Set<ResolvedArtifact> artifacts = utils.getAllArtifacts([dependency] as Set)

        then:
        artifacts == [artifact] as Set
        utils.getArtifactConfigurationMap()[artifact] == 'runtimeClasspath'
    }
}
