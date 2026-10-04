package se.kth.depclean.utils.json

import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedDependency
import se.kth.depclean.analysis.DefaultGradleProjectDependencyAnalyzer
import spock.lang.Specification
import spock.lang.TempDir

class JsonResultWriterSpec extends Specification {

    @TempDir
    File directory

    def "writes project and dependency metadata with usage classifications"() {
        given:
        Project project = Mock()
        project.getGroup() >> 'com.example'
        project.getName() >> 'application'
        project.getVersion() >> '1.0.0'
        ResolvedDependency dependency = Mock()
        dependency.getName() >> 'org.example:library:2.0.0'
        dependency.getConfiguration() >> 'runtimeClasspath'
        dependency.getModuleGroup() >> 'org.example'
        dependency.getModuleVersion() >> '2.0.0'
        dependency.getChildren() >> ([] as Set)
        dependency.getParents() >> ([] as Set)
        File output = new File(directory, 'result.json')

        when:
        new JsonResultWriter(
            project,
            new File(directory, 'class-usage.csv'),
            new DefaultGradleProjectDependencyAnalyzer(false),
            ['application-1.0.0.jar': 17L, 'library-2.0.0.jar': 42L],
            false,
            [dependency] as Set,
            ['org.example:library:2.0.0:runtimeClasspath'] as Set,
            [] as Set,
            [] as Set,
            [] as Set,
            [] as Set,
            [] as Set
        ).write(new FileWriter(output))

        then:
        String json = output.text
        json.contains('"id": "com.example:application:1.0.0"')
        json.contains('"id": "org.example:library:2.0.0"')
        json.contains('"type": "direct"')
        json.contains('"status": "used"')
        json.contains('"size": 42')
        json.contains('"children(s)": []')
    }
}
