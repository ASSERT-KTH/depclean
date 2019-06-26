package se.kth.jdbl.pom;

import org.apache.maven.model.*;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CustomFileWriter {

    /**
     * Writes a file with descriptive fields of the studied artifacts locally.
     *
     * @param descriptionPath
     * @param model
     * @throws IOException
     */
    public static void writeArtifactProperties(String descriptionPath, Model model) throws IOException {

        BufferedWriter bw = new BufferedWriter(new FileWriter(descriptionPath, true));

        // write coordinates
        String artifact = model.getGroupId() + ":" + model.getArtifactId() + ":" + model.getPackaging() + ":" + model.getVersion();
        bw.write(artifact + ",");

        // write organization
        Organization organization = model.getOrganization();
        if (organization != null) {
            bw.write(organization.getName().replaceAll(",", "[comma]").replaceAll("\n", " ") + ",");
        } else {
            bw.write("NA,");
        }

        // write scm
        Scm scm = model.getScm();
        if (scm != null) {
            if (scm.getUrl() != null) {
                bw.write(scm.getUrl().replaceAll(",", "[comma]").replaceAll("\n", " ") + ",");
            }
        } else {
            bw.write("NA,");
        }

        // write CI management
        CiManagement ciManagement = model.getCiManagement();
        if (ciManagement != null) {
            if (ciManagement.getSystem() != null) {
                bw.write(ciManagement.getSystem().replaceAll(",", "[comma]").replaceAll("\n", " "));
            }
        } else {
            bw.write("NA,");
        }

        // write licences
        List<License> licencesList = model.getLicenses();
        if (!licencesList.isEmpty()) {
            bw.write(licencesList.get(0).getName().replaceAll(",", "[comma]").replaceAll("\n", " ") + ",");
        } else {
            bw.write("NA,");
        }

        // write project description
        if (model.getDescription() != null) {
            bw.write(model.getDescription().replaceAll(",", "[comma]").replaceAll("\n", " ") + "\n");
        } else {
            bw.write("NA," + "\n");
        }

        bw.close();
    }

    /**
     * Writes the results to a file locally.
     *
     * @param resultsPath
     * @param artifact
     * @param dependencies
     * @throws IOException
     */
    public static void writeDependencyResults(String resultsPath,
                                              String artifact,
                                              ArrayList<MavenDependency> dependencies) throws IOException {

        BufferedWriter bw = new BufferedWriter(new FileWriter(resultsPath, true));

        for (MavenDependency dependency : dependencies) {
            // write artifact coordinates
            bw.write(artifact + ",");
            bw.write(dependency.toString() );
        }
        bw.close();
    }
}
