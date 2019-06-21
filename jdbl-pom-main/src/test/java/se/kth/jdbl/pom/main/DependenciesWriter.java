package se.kth.jdbl.pom.main;

import org.apache.maven.artifact.Artifact;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DependenciesWriter {

    /**
     * Writes the results to a file locally.
     *
     * @param filePath
     * @param artifact
     * @param usedDeclared
     * @param usedButUndeclared
     * @param unusedButDeclared
     * @throws IOException
     */
    public static void writeResults(String filePath,
                              String artifact,
                              Set<Artifact> usedDeclared,
                              Set<Artifact> usedButUndeclared,
                              Set<Artifact> unusedButDeclared,
                              ArrayList<String> directDependencies,
                              ArrayList<String> transitiveDependencies,
                              ArrayList<String> allDependencies) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(filePath));

        int max = allDependencies.size();

        List<Artifact> usedDeclaredList = new ArrayList(usedDeclared);
        List<Artifact> usedButUndeclaredList = new ArrayList(usedButUndeclared);
        List<Artifact> unusedButDeclaredList = new ArrayList(unusedButDeclared);

        // write header
        writer.write("Artifact,UsedDeclared,UsedUndeclared,UnusetDeclared,DirectDep,TransDep,AllDep" + "\n");

        for (int i = 0; i < max; i++) {
            // write artifact coordinates
            writer.write(artifact + ",");

            // write usedDeclared dependencies
            if (usedDeclaredList.size() > i) {
                writer.append(usedDeclaredList.get(i).toString() + ",");
            } else {
                writer.append("NA,");
            }

            // write usedButUndeclared dependencies
            if (usedButUndeclaredList.size() > i) {
                writer.append(usedButUndeclaredList.get(i).toString() + ",");
            } else {
                writer.append("NA,");
            }

            // write unusedButDeclared dependencies
            if (unusedButDeclaredList.size() > i) {
                writer.append(unusedButDeclaredList.get(i).toString() + ",");
            } else {
                writer.append("NA,");
            }

            // write direct dependencies
            if (directDependencies.size() > i) {
                writer.append(directDependencies.get(i) + ",");
            } else {
                writer.append("NA,");
            }

            // write transitive dependencies
            if (transitiveDependencies.size() > i) {
                writer.append(transitiveDependencies.get(i) + ",");
            } else {
                writer.append("NA,");
            }

            // write transitive dependencies
            if (allDependencies.size() > i) {
                writer.append(allDependencies.get(i) + "\n");
            } else {
                writer.append("NA," + "\n");
            }
        }
        writer.close();
    }
}
