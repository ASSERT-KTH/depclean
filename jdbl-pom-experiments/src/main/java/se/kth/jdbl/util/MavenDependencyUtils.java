package se.kth.jdbl.util;

public class MavenDependencyUtils {

    //-------------------------------/
    //------- PUBLIC METHOD/S -------/
    //-------------------------------/

    public static String toCoordinates(String groupId, String artifactId, String  version){
        return groupId + ":" + artifactId + ":" + version;
    }
}
