package se.kth.jdbl.pom;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class PomDownloader {

    /**
     * Download pom file from the Maven Central repository.
     *
     * @param artifactDir
     * @param groupId
     * @param artifactId
     * @param version
     * @throws IOException
     */
    public static void downloadPom(String artifactDir, String groupId, String artifactId, String version) throws IOException {
        FileUtils.copyURLToFile(
                new URL("http://central.maven.org/maven2/" +
                        groupId.replace('.', '/') + "/" +
                        artifactId + "/" +
                        version + "/" +
                        artifactId + "-" + version + ".pom"),
                new File(artifactDir + "pom.xml"));

    }
}
