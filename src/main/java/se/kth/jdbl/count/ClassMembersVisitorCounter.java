package se.kth.jdbl.count;

public class ClassMembersVisitorCounter {

    //--------------------------/
    //------ CLASS FIELDS ------/
    //--------------------------/

    private static long nbVisitedTypes;
    private static long nbVisitedFields;
    private static long nbVisitedMethods;
    private static long nbVisitedAnnotations;

    //--------------------------/
    //------ CONSTRUCTORS ------/
    //--------------------------/

    private ClassMembersVisitorCounter() {
        throw new IllegalStateException("Utility class");
    }

    //--------------------------/
    //----- PUBLIC METHODS -----/
    //--------------------------/

    public static void resetClassCounters() {
        nbVisitedTypes = 0;
        nbVisitedFields = 0;
        nbVisitedMethods = 0;
        nbVisitedAnnotations = 0;
    }

    public static void markAsNotFoundClassCounters() {
        nbVisitedTypes = -1;
        nbVisitedFields = -1;
        nbVisitedMethods = -1;
        nbVisitedAnnotations = -1;
    }

    public static void addVisitedClass() {
        nbVisitedTypes++;
    }

    public static void addVisitedField() {
        nbVisitedFields++;
    }

    public static void addVisitedMethod() {
        nbVisitedMethods++;
    }

    public static void addVisitedAnnotation() {
        nbVisitedAnnotations++;
    }

    //--------------------------/
    //---- GETTER METHODS ------/
    //--------------------------/

    public static long getNbVisitedTypes() {
        return nbVisitedTypes;
    }

    public static long getNbVisitedFields() {
        return nbVisitedFields;
    }

    public static long getNbVisitedMethods() {
        return nbVisitedMethods;
    }

    public static long getNbVisitedAnnotations() {
        return nbVisitedAnnotations;
    }

}