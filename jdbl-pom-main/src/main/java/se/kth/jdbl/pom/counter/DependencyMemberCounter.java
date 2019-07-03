package se.kth.jdbl.pom.counter;

import org.apache.maven.artifact.Artifact;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DependencyMemberCounter {

    //--------------------------/
    //------ CLASS FIELDS ------/
    //--------------------------/

    private static Map<String , Set<String>> artifactClassMap;

    //--------------------------/
    //----- PUBLIC METHODS -----/
    //--------------------------/

    public static void resetDependencyMemberCounter() {
        artifactClassMap = new HashMap<>();
    }

    //--------------------------/
    //---- SETTER METHODS ------/
    //--------------------------/

    public static void setArtifactClassMap(Map<String , Set<String>> artifactClassMap) {
        DependencyMemberCounter.artifactClassMap = artifactClassMap;
    }

    //--------------------------/
    //---- GETTER METHODS ------/
    //--------------------------/

    public static Map<String , Set<String>> getArtifactClassMap() {
        return artifactClassMap;
    }
}
