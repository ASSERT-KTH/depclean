package se.kth.jdbl.pom;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.*;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
     * @param usedDeclared
     * @param usedUndeclared
     * @param unusedDeclared
     * @throws IOException
     */
    public static void writeDependencyResults(String resultsPath,
                                              String artifact,
                                              Set<Artifact> usedDeclared,
                                              Set<Artifact> usedUndeclared,
                                              Set<Artifact> unusedDeclared,
                                              ArrayList<String> unusedUndeclared,
                                              ArrayList<String> directDependencies,
                                              ArrayList<String> transitiveDependencies,
                                              ArrayList<String> allDependencies) throws IOException {

        BufferedWriter bw = new BufferedWriter(new FileWriter(resultsPath, true));

        int max = allDependencies.size();

        List<Artifact> usedDeclaredList = new ArrayList(usedDeclared);
        List<Artifact> usedButUndeclaredList = new ArrayList(usedUndeclared);
        List<Artifact> unusedButDeclaredList = new ArrayList(unusedDeclared);

        for (int i = 0; i < max; i++) {
            // write artifact coordinates
            bw.write(artifact + ",");

            // write usedDeclared dependencies
            if (usedDeclaredList.size() > i) {
                bw.write(usedDeclaredList.get(i).toString() + ",");
            } else {
                bw.write("NA,");
            }

            // write usedButUndeclared dependencies
            if (usedButUndeclaredList.size() > i) {
                bw.write(usedButUndeclaredList.get(i).toString() + ",");
            } else {
                bw.write("NA,");
            }

            // write unusedButDeclared dependencies
            if (unusedButDeclaredList.size() > i) {
                bw.write(unusedButDeclaredList.get(i).toString() + ",");
            } else {
                bw.write("NA,");
            }

            // write unusedButDeclared dependencies
            if (unusedUndeclared.size() > i) {
                bw.write(unusedUndeclared.get(i) + ",");
            } else {
                bw.write("NA,");
            }

            // write direct dependencies
            if (directDependencies.size() > i) {
                bw.write(directDependencies.get(i) + ",");
            } else {
                bw.write("NA,");
            }

            // write transitive dependencies
            if (transitiveDependencies.size() > i) {
                bw.write(transitiveDependencies.get(i) + ",");
            } else {
                bw.write("NA,");
            }

            // write transitive dependencies
            if (allDependencies.size() > i) {
                bw.write(allDependencies.get(i) + "\n");
            } else {
                bw.write("NA," + "\n");
            }
        }
        bw.close();
    }
}
