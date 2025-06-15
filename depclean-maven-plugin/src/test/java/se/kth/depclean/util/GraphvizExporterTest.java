package se.kth.depclean.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import se.kth.depclean.core.model.Dependency;
import se.kth.depclean.graph.MavenDependencyGraph;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GraphvizExporterTest {
    private final File outputFile = new File("target/test-dependency-graph.png");

    @AfterEach
    void cleanup() {
        if (outputFile.exists()) {
            outputFile.delete();
        }
    }

    @Test
    void testExportCreatesImageFile() throws IOException {
        // Mock a simple MavenDependencyGraph
        MavenDependencyGraph graph = mock(MavenDependencyGraph.class);
        Dependency depA = new Dependency("g1", "a1", "1.0", null);
        Dependency depB = new Dependency("g2", "a2", "2.0", null);
        Set<Dependency> allDeps = new HashSet<>();
        allDeps.add(depA);
        allDeps.add(depB);
        when(graph.allDependencies()).thenReturn(allDeps);
        when(graph.getDependenciesForParent(depA)).thenReturn(Collections.singleton(depB));
        when(graph.getDependenciesForParent(depB)).thenReturn(Collections.emptySet());

        GraphvizExporter.export(graph, outputFile);
        assertTrue(outputFile.exists(), "Output image file should exist");
        assertTrue(outputFile.length() > 0, "Output image file should not be empty");
    }
}