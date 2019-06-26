package se.kth.jdbl.pom;

public class MavenDependency {

    private String coordinates;
    private String type;
    private String scope;

    private boolean isOptional;

    private boolean isDirect;
    private boolean isTransitive;

    private boolean isUsedDeclared;
    private boolean isUsedUndeclared;

    private boolean isUnusedDeclared;
    private boolean isUnusedUndeclared;

    private String inConflict;

    public MavenDependency() {
    }

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

    public MavenDependency isDirect(boolean isDirect) {
        this.isDirect = isDirect;
        return this;
    }

    public MavenDependency isTransitive(boolean isTransitive) {
        this.isTransitive = isTransitive;
        return this;
    }

    public MavenDependency isUsedDeclared(boolean isUsedDeclared) {
        this.isUsedDeclared = isUsedDeclared;
        return this;
    }

    public MavenDependency isUsedUndeclared(boolean isUsedUndeclared) {
        this.isUsedUndeclared = isUsedUndeclared;
        return this;
    }

    public MavenDependency isUnusedDeclared(boolean isUnusedDeclared) {
        this.isUnusedDeclared = isUnusedDeclared;
        return this;
    }

    public MavenDependency isUnusedUndeclared(boolean isUnusedUndeclared) {
        this.isUnusedUndeclared = isUnusedUndeclared;
        return this;
    }

    public MavenDependency inConflict(String inConflict) {
        this.inConflict = inConflict;
        return this;
    }

    @Override
    public String toString() {
        return coordinates + "," + type + "," + scope + "," + isOptional + "," + isDirect + "," + isTransitive + "," + isUsedDeclared + "," +
                isUsedUndeclared + "," + isUnusedDeclared + "," + isUnusedUndeclared + "," + inConflict + "\n";
    }
}
