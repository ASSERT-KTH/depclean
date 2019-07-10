package se.kth.jdbl.util;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class PomDownloader {

    //--------------------------/
    //------ CONSTRUCTORS ------/
    //--------------------------/

    private PomDownloader() {
    }

    //--------------------------/
    //----- PUBLIC METHODS -----/
    //--------------------------/

    /**
     * This method downloads a pom file from the Maven Central repository.
     *
     * @param artifactDir Directory to put the pom file.
     * @param groupId     The artifact groupId.
     * @param artifactId  The artifact artifactId.
     * @param version     The artifact version.
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
