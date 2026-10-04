package se.kth.depclean.analysis

import org.gradle.api.artifacts.ResolvedArtifact
import spock.lang.Specification

class GradleProjectDependencyAnalysisSpec extends Specification {

    def "copies inputs, exposes immutable results, and renders all categories"() {
        given:
        ResolvedArtifact direct = Mock()
        ResolvedArtifact transitive = Mock()
        ResolvedArtifact unused = Mock()
        Set<ResolvedArtifact> directInput = [direct] as Set
        GradleProjectDependencyAnalysis analysis = new GradleProjectDependencyAnalysis(
            directInput, [transitive] as Set, [unused] as Set)

        when:
        directInput.clear()

        then:
        analysis.usedDeclaredArtifacts == [direct] as Set
        analysis.usedUndeclaredArtifacts == [transitive] as Set
        analysis.unusedDeclaredArtifacts == [unused] as Set
        analysis.toString().contains('usedDeclaredArtifacts=')
        analysis.toString().contains('usedUndeclaredArtifacts=')
        analysis.toString().contains('unusedDeclaredArtifacts=')

        when:
        analysis.usedDeclaredArtifacts.add(Mock(ResolvedArtifact))

        then:
        thrown(UnsupportedOperationException)
    }

    def "treats null inputs as empty and compares all categories"() {
        given:
        GradleProjectDependencyAnalysis empty = new GradleProjectDependencyAnalysis(null, null, null)
        GradleProjectDependencyAnalysis same = new GradleProjectDependencyAnalysis([] as Set, [] as Set, [] as Set)

        expect:
        empty.usedDeclaredArtifacts.empty
        empty.usedUndeclaredArtifacts.empty
        empty.unusedDeclaredArtifacts.empty
        empty == same
        empty.hashCode() == same.hashCode()
        empty != 'not an analysis'
        empty.toString().endsWith('[]')
    }
}
