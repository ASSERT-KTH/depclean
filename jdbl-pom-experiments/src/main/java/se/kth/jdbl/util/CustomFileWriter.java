package se.kth.jdbl.util;

import org.apache.maven.model.*;
import se.kth.jdbl.counter.ClassMembersVisitorCounter;
import se.kth.jdbl.tree.StandardTextVisitor;
import se.kth.jdbl.tree.analysis.DependencyTreeAnalyzer;

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
     */
    public static void writeArtifactProperties(String descriptionPath, Model model, String coordinates, DependencyTreeAnalyzer dta, DependencyTreeAnalyzer dtaDebloated) throws IOException {
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
            bw.write(model.getDescription().replaceAll(",", "[comma]").replaceAll("\n", " ") + ",");
        } else {
            bw.write("NA,");
        }

        // write height of original and debloated dependency trees
        bw.write(dta.heightOfDependencyTree() + "," + dtaDebloated.heightOfDependencyTree() + "\n");

        bw.close();
    }

    /**
     * Writes the results to a file locally.
     */
    public static void writeDependencyResults(String resultsPath, String artifact, ArrayList<MavenDependencyBuilder> dependencies) throws IOException {
        BufferedWriter bw = new BufferedWriter(new FileWriter(resultsPath, true));
        for (MavenDependencyBuilder dependency : dependencies) {
            // write artifact coordinates
            bw.write(artifact + ",");
            bw.write(dependency.toString());
        }
        bw.close();
    }

    /**
     * Writes the debloated pom file.
     */
    public static void writeDebloatedPom(StandardTextVisitor standardTextVisitor, String debloatedPomPath) throws IOException {
        BufferedWriter bw = new BufferedWriter(new FileWriter(debloatedPomPath));
        bw.write(standardTextVisitor.toString());
        bw.close();
    }
}
