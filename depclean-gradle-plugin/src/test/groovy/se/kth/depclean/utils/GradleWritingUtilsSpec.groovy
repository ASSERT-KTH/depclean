package se.kth.depclean.utils

import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedModuleVersion
import se.kth.depclean.DepCleanGradleAction
import spock.lang.Specification

class GradleWritingUtilsSpec extends Specification {

    private static final String MODULE_VERSION = 'com.example:lib:1.0.0'
    private static final String COORDINATES = 'com.example:lib:1.0.0'

    private final DependencyUtils utils = new DependencyUtils()

    def setup() {
        def configurationMap = DepCleanGradleAction.getDeclaredField('ArtifactConfigurationMap')
        configurationMap.accessible = true
        configurationMap.set(null, utils.getArtifactConfigurationMap())
    }

    private ResolvedArtifact artifactOf(String moduleVersion, String configuration) {
        ResolvedModuleVersion resolvedModuleVersion = Mock()
        resolvedModuleVersion.toString() >> moduleVersion

        ResolvedArtifact artifact = Mock()
        artifact.getModuleVersion() >> resolvedModuleVersion
        if (configuration != null) {
            utils.getArtifactConfigurationMap().put(artifact, configuration)
        }
        artifact
    }

    private void assertMapsTo(String modernConfiguration, String resolvedConfiguration) {
        ResolvedArtifact artifact = artifactOf(MODULE_VERSION, resolvedConfiguration)
        def multimap = GradleWritingUtils.getNewConfigurations([artifact] as Set)
        assert multimap.get(modernConfiguration) == [COORDINATES]
    }

    def cleanup() {
        utils.getArtifactConfigurationMap().clear()
    }

    def "writes the legacy compile and default configurations back as implementation"() {
        expect:
        assertMapsTo('implementation', 'compile')
        assertMapsTo('implementation', 'default')
    }

    def "writes the legacy runtime configuration back as runtimeOnly"() {
        expect: 'a runtime dependency must not land back on the compile classpath'
        assertMapsTo('runtimeOnly', 'runtime')
    }

    def "writes the legacy test configurations back into the test source set"() {
        expect:
        assertMapsTo('testImplementation', 'testCompile')
        assertMapsTo('testRuntimeOnly', 'testRuntime')
    }

    def "keeps production configurations out of the test source set"() {
        expect: 'the trailing "Elements" must not be read as "test"'
        assertMapsTo('implementation', 'runtimeElements')
        assertMapsTo('implementation', 'apiElements')
        assertMapsTo('implementation', 'compileClasspath')
    }

    def "sends any other test prefixed configuration to the test source set"() {
        expect:
        assertMapsTo('testImplementation', 'testAnnotationProcessor')
        assertMapsTo('testImplementation', 'testFixtures')
    }

    def "falls back to implementation for an artifact with no known configuration"() {
        expect:
        assertMapsTo('implementation', null)
    }

    def "splits the dependencies over the configurations they belong to"() {
        given:
        ResolvedArtifact compileArtifact = artifactOf('com.example:compile-dep:1.0.0', 'compile')
        ResolvedArtifact runtimeArtifact = artifactOf('com.example:runtime-dep:1.0.0', 'runtime')

        when:
        def multimap = GradleWritingUtils.getNewConfigurations([compileArtifact, runtimeArtifact] as Set)

        then:
        multimap.get('implementation') == ['com.example:compile-dep:1.0.0']
        multimap.get('runtimeOnly') == ['com.example:runtime-dep:1.0.0']
    }
}
