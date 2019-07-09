package se.kth.jdbl.util;

public class MavenDependencyBuilder {

    //--------------------------/
    //------ CLASS FIELDS ------/
    //--------------------------/

    private String coordinates;
    private String type;
    private String scope;
    private String dependencyType;
    private String inConflict;

    private long nbTypes;
    private long nbFields;
    private long nbMethods;
    private long nbAnnotations;

    private int nbDependencies;
    private int treeLevel;

    private boolean isOptional;
    private boolean isUsed;
    private boolean isDeclared;
    private boolean isRemovable;

    //--------------------------/
    //----- PUBLIC METHODS -----/
    //--------------------------/

    public MavenDependencyBuilder setCoordinates(String coordinates) {
        this.coordinates = coordinates;
        return this;
    }

    public MavenDependencyBuilder setType(String type) {
        this.type = type;
        return this;
    }

    public MavenDependencyBuilder setScope(String scope) {
        this.scope = scope;
        return this;
    }

    public MavenDependencyBuilder isOptional(boolean isOptional) {
        this.isOptional = isOptional;
        return this;
    }

    public MavenDependencyBuilder isDeclared(boolean isDeclared) {
        this.isDeclared = isDeclared;
        return this;
    }

    public MavenDependencyBuilder isRemovable(boolean isRemovable) {
        this.isRemovable = isRemovable;
        return this;
    }

    public MavenDependencyBuilder isUsed(boolean isUsed) {
        this.isUsed = isUsed;
        return this;
    }

    public MavenDependencyBuilder setNbTypes(long nbTypes) {
        this.nbTypes = nbTypes;
        return this;
    }

    public MavenDependencyBuilder setNbFields(long nbFields) {
        this.nbFields = nbFields;
        return this;
    }

    public MavenDependencyBuilder setNbMethods(long nbMethods) {
        this.nbMethods = nbMethods;
        return this;
    }

    public MavenDependencyBuilder setNbAnnotations(long nbAnnotations) {
        this.nbAnnotations = nbAnnotations;
        return this;
    }

    public MavenDependencyBuilder setNbDependencies(int nbDependencies) {
        this.nbDependencies = nbDependencies;
        return this;
    }

    public MavenDependencyBuilder setTreeLevel(int treeLevel) {
        this.treeLevel = treeLevel;
        return this;
    }

    public MavenDependencyBuilder setDependencyType(String dependencyType) {
        this.dependencyType = dependencyType;
        return this;
    }

    public MavenDependencyBuilder inConflict(String inConflict) {
        this.inConflict = inConflict;
        return this;
    }

    @Override
    public String toString() {
        return coordinates + "," +
                type + "," +
                scope + "," +
                isOptional + "," +
                dependencyType + "," +
                isUsed + "," +
                isDeclared + "," +
                isRemovable + "," +
                nbTypes + "," +
                nbFields + "," +
                nbMethods + "," +
                nbAnnotations + "," +
                nbDependencies + "," +
                treeLevel + "," +
                inConflict + "\n";
    }
}
