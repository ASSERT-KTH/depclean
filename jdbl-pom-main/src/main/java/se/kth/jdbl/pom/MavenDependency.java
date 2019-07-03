package se.kth.jdbl.pom;

public class MavenDependency {

    //--------------------------/
    //------ CLASS FIELDS ------/
    //--------------------------/

    private String coordinates;
    private String type;
    private String scope;
    private String dependencyType;
    private String inConflict;

    private int nbDependencies;
    private int treeLevel;

    private boolean isOptional;
    private boolean isUsed;
    private boolean isDeclared;

    //--------------------------/
    //----- PUBLIC METHODS -----/
    //--------------------------/

    public MavenDependency setCoordinates(String coordinates) {
        this.coordinates = coordinates;
        return this;
    }

    public MavenDependency setType(String type) {
        this.type = type;
        return this;
    }

    public MavenDependency setScope(String scope) {
        this.scope = scope;
        return this;
    }

    public MavenDependency isOptional(boolean isOptional) {
        this.isOptional = isOptional;
        return this;
    }

    public MavenDependency isDeclared(boolean isDeclared) {
        this.isDeclared = isDeclared;
        return this;
    }

    public MavenDependency isUsed(boolean isUsed) {
        this.isUsed = isUsed;
        return this;
    }

    public MavenDependency setNbDependencies(int nbDependencies) {
        this.nbDependencies = nbDependencies;
        return this;
    }

    public MavenDependency setTreeLevel(int treeLevel) {
        this.treeLevel = treeLevel;
        return this;
    }

    public MavenDependency setDependencyType(String dependencyType) {
        this.dependencyType = dependencyType;
        return this;
    }

    public MavenDependency inConflict(String inConflict) {
        this.inConflict = inConflict;
        return this;
    }

    @Override
    public String toString() {
        return coordinates + "," + type + "," + scope + "," + isOptional + "," + dependencyType + "," + isUsed + "," + isDeclared + "," +
                nbDependencies + "," + treeLevel + "," + inConflict + "\n";
    }
}
