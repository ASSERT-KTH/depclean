package se.kth.depclean.core.analysis;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public interface ArtifactCreator {

  @NotNull
  default Artifact createArtifact(String name) {
    final File jarFile = new File("src/test/resources/analysisResources/" + name + ".jar");
    final Artifact artifact = new DefaultArtifact("se.kth.depclean.core.analysis", name,
        "1.0.0", "compile", "jar", "", new DefaultArtifactHandler());
    artifact.setFile(jarFile);
    return artifact;
  }
}
