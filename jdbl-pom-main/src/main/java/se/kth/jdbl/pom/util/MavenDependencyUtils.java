package se.kth.jdbl.pom.util;

public class MavenDependencyUtils {

    public static String toCoordinates(String groupId, String artifactId, String  version){
        return groupId + ":" + artifactId + ":" + version;
    }
}
