package se.kth.jdbl.pom.util;

import org.apache.maven.model.*;
import se.kth.jdbl.pom.MavenDependency;
import se.kth.jdbl.pom.counter.ClassMembersVisitorCounter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CustomFileWriter {

    //--------------------------/
    //----- PUBLIC METHODS -----/
    //--------------------------/

    /**
     * Writes a file with descriptive fields of the studied artifacts locally.
     *
     * @param descriptionPath
     * @param model
     * @throws IOException
     */
    public static void writeArtifactProperties(String descriptionPath, Model model, String coordinates) throws IOException {

        BufferedWriter bw = new BufferedWriter(new FileWriter(descriptionPath, true));

        // write coordinates
        bw.write(coordinates + ",");

        // write static analysis stats
        bw.write(ClassMembersVisitorCounter.getNbVisitedTypes() + "," +
                ClassMembersVisitorCounter.getNbVisitedFields() + "," +
                ClassMembersVisitorCounter.getNbVisitedMethods() + "," +
                ClassMembersVisitorCounter.getNbVisitedAnnotations() + ",");

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
            bw.write(dependency.toString());
        }
        bw.close();
    }
}
