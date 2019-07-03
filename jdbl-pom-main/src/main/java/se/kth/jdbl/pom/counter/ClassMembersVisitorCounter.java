package se.kth.jdbl.pom.counter;

public class ClassMembersVisitorCounter {

    //--------------------------/
    //------ CLASS FIELDS ------/
    //--------------------------/

    private static long nbVisitedClasses;
    private static long nbVisitedFields;
    private static long nbVisitedMethods;
    private static long nbVisitedAnnotations;

    //--------------------------/
    //------ CONSTRUCTORS ------/
    //--------------------------/

    public static void resetClassCounters() {
        nbVisitedClasses = 0;
        nbVisitedFields = 0;
        nbVisitedMethods = 0;
        nbVisitedAnnotations = 0;
    }

    //--------------------------/
    //----- PUBLIC METHODS -----/
    //--------------------------/

    public static void addVisitedClass() {
        nbVisitedClasses++;
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

    public static long getNbVisitedClasses() {
        return nbVisitedClasses;
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