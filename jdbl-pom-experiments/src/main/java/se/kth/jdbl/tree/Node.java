package se.kth.jdbl.tree;

import java.io.Serializable;
import java.util.LinkedList;

/**
 * This class represents a node in the Maven Dependency Tree.
 */
public class Node implements Serializable {

    //--------------------------/
    //------ CLASS FIELDS ------/
    //--------------------------/

    private static final long serialVersionUID = 5530155206443082802L;

    private final String groupId;

    private final String artifactId;

    private final String packaging;

    private final String classifier;

    private final String version;

    private final String scope;

    private final String description;

    private final boolean omitted;

    private Node parent;

    private final LinkedList<Node> childNodes = new LinkedList<Node>();

    private boolean isUsed = false;

    private boolean isInConflict = false;

    //--------------------------/
    //------ CONSTRUCTORS ------/
    //--------------------------/

    public Node(
            final String groupId,
            final String artifactId,
            final String packaging,
            final String classifier,
            final String version,
            final String scope,
            final String description,
            boolean omitted) {
        super();
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.packaging = packaging;
        this.classifier = classifier;
        this.version = version;
        this.scope = scope;
        this.description = description;
        this.omitted = omitted;
    }

    //--------------------------/
    //---- GETTER METHODS ------/
    //--------------------------/

    public String getGroupId() {
        return this.groupId;
    }

    public String getArtifactId() {
        return this.artifactId;
    }

    public String getPackaging() {
        return this.packaging;
    }

    public String getClassifier() {
        return this.classifier;
    }

    public String getVersion() {
        return this.version;
    }

    public String getDescription() {
        return this.description;
    }

    public String getScope() {
        return this.scope;
    }

    public boolean isUsed() {
        return isUsed;
    }

    public boolean isInConflict() {
        return isInConflict;
    }

    public void setInConflict(boolean inConflict) {
        isInConflict = inConflict;
    }

    public boolean isOmitted() {
        return omitted;
    }

    public Node getParent() {
        return parent;
    }

    public LinkedList<Node> getChildNodes() {
        return this.childNodes;
    }

    public boolean addChildNode(final Node o) {
        o.parent = this;
        return this.childNodes.add(o);
    }

    public boolean remove(final Node o) {
        return this.childNodes.remove(o);
    }

    public Node getChildNode(int index) {
        return childNodes.get(index);
    }

    public Node getFirstChildNode() {
        return childNodes.getFirst();
    }

    public Node getLastChildNode() {
        return childNodes.getLast();
    }

    //--------------------------/
    //---- SETTER METHODS ------/
    //--------------------------/

    public void setUsed(boolean used) {
        isUsed = used;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((artifactId == null) ? 0 : artifactId.hashCode());
        result = prime * result + ((childNodes == null) ? 0 : childNodes.hashCode());
        result = prime * result + ((classifier == null) ? 0 : classifier.hashCode());
        result = prime * result + ((description == null) ? 0 : description.hashCode());
        result = prime * result + ((groupId == null) ? 0 : groupId.hashCode());
        result = prime * result + (omitted ? 1231 : 1237);
        result = prime * result + ((packaging == null) ? 0 : packaging.hashCode());
        result = prime * result + ((scope == null) ? 0 : scope.hashCode());
        result = prime * result + ((version == null) ? 0 : version.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Node other = (Node) obj;
        if (artifactId == null) {
            if (other.artifactId != null) {
                return false;
            }
        } else if (!artifactId.equals(other.artifactId)) {
            return false;
        }
        if (childNodes == null) {
            if (other.childNodes != null) {
                return false;
            }
        } else if (!childNodes.equals(other.childNodes)) {
            return false;
        }
        if (classifier == null) {
            if (other.classifier != null) {
                return false;
            }
        } else if (!classifier.equals(other.classifier)) {
            return false;
        }
        if (description == null) {
            if (other.description != null) {
                return false;
            }
        } else if (!description.equals(other.description)) {
            return false;
        }
        if (groupId == null) {
            if (other.groupId != null) {
                return false;
            }
        } else if (!groupId.equals(other.groupId)) {
            return false;
        }
        if (omitted != other.omitted) {
            return false;
        }
        if (packaging == null) {
            if (other.packaging != null) {
                return false;
            }
        } else if (!packaging.equals(other.packaging)) {
            return false;
        }
        if (scope == null) {
            if (other.scope != null) {
                return false;
            }
        } else if (!scope.equals(other.scope)) {
            return false;
        }
        if (version == null) {
            if (other.version != null) {
                return false;
            }
        } else if (!version.equals(other.version)) {
            return false;
        }
        return true;
    }

    @Override
    public Node clone() {
        final Node clone = new Node(
                this.groupId,
                this.artifactId,
                this.packaging,
                this.classifier,
                this.version,
                this.scope,
                this.description,
                this.omitted
        );
        for (final Node childNode : this.childNodes) {
            clone.addChildNode(childNode.clone());
        }
        //the clone has no parent
        return clone;
    }

    @Override
    public String toString() {
        StandardTextVisitor visitor = new StandardTextVisitor();
        //consider the current node as a root node
        visitor.visit(this.parent == null ? this : this.clone());
        return visitor.toString();
    }

    public String getArtifactCanonicalForm() {
        final StringBuilder builder = new StringBuilder();
        if (omitted) {
            builder.append("(");
        }
        builder.append(this.groupId);
        builder.append(":");
        builder.append(this.artifactId);
        builder.append(":");
        builder.append(this.packaging);
        if (this.classifier != null) {
            builder.append(":");
            builder.append(this.classifier);
        }
        builder.append(":");
        builder.append(this.version);
        if (this.scope != null) {
            builder.append(":");
            builder.append(this.scope);
        }
        if (omitted) {
            builder.append(" - ");
            builder.append(this.description);
            builder.append(")");
        } else if (this.description != null) {
            builder.append(" (");
            builder.append(this.description);
            builder.append(")");
        }
        return builder.toString();
    }

}
