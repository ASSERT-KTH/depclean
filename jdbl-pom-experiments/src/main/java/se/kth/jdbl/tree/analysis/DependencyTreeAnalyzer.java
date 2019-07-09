package se.kth.jdbl.tree.analysis;

import org.apache.maven.artifact.Artifact;
import se.kth.jdbl.tree.InputType;
import se.kth.jdbl.tree.Node;
import se.kth.jdbl.tree.ParseException;
import se.kth.jdbl.tree.Parser;
import se.kth.jdbl.util.MavenDependencyUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DependencyTreeAnalyzer implements Serializable {

    //-------------------------------/
    //-------- CLASS FIELD/S --------/
    //-------------------------------/

    private Node rootNode;

    private int nbConflictsAvoidedWithDebloating = 0;

    //-------------------------------/
    //-------- CONSTRUCTOR/S --------/
    //-------------------------------/

    public DependencyTreeAnalyzer(String dependencyTreeFile) {
        InputType type = InputType.TEXT;
        try {
            Reader r = new BufferedReader(new InputStreamReader(new FileInputStream(dependencyTreeFile), "UTF-8"));
            Parser parser = type.newParser();
            rootNode = parser.parse(r);
        } catch (ParseException | FileNotFoundException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    //-------------------------------/
    //------- PUBLIC METHOD/S -------/
    //-------------------------------/

    /**
     * Remove the direct dependencies for which all their children (direct and transitive) are unused.
     */
    public void removeUnusedArtifacts() {
        for (int i = 0; i < rootNode.getChildNodes().size(); i++) {
            Node childNode = rootNode.getChildNode(i);
            if (canBeRemoved(childNode)) {
                countConflictsAvoided(childNode);
                rootNode.remove(childNode);
            }
        }
    }

    public void labelNodes(Set<Artifact> usedDeclaredDependencies, Set<Artifact> usedUndeclaredDependencies) {
        labelNodes(usedDeclaredDependencies, usedUndeclaredDependencies, rootNode);
    }

    public List<String> getDirectDependencies() {
        List<Node> directDependenciesNodes = rootNode.getChildNodes();
        ArrayList<String> directDependenciesCanonical = new ArrayList();
        for (Node dependency : directDependenciesNodes) {
            directDependenciesCanonical.add(dependency.getArtifactCanonicalForm());
        }
        return directDependenciesCanonical;
    }

    public List<String> getAllDependenciesCanonical(Node node) {
        List<Node> allDependenciesNodes = returnAllNodes(node);
        allDependenciesNodes.remove(node);
        ArrayList<String> allDependenciesCanonical = new ArrayList();
        for (Node dependency : allDependenciesNodes) {
            allDependenciesCanonical.add(dependency.getArtifactCanonicalForm());
        }
        return allDependenciesCanonical;
    }

    public List<String> getAllDependenciesCoordinates(Node node) {
        List<Node> allDependenciesNodes = returnAllNodes(node);
        allDependenciesNodes.remove(node);
        ArrayList<String> allDependenciesCanonical = new ArrayList();
        for (Node dependency : allDependenciesNodes) {
            allDependenciesCanonical.add(MavenDependencyUtils.toCoordinates(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion()));
        }
        return allDependenciesCanonical;
    }

    public int getNumberOfDependenciesOfNode(String groupId, String artifactId, String version) {
        List<Node> allDependenciesNodes = returnAllNodes(rootNode);
        allDependenciesNodes.remove(rootNode);

        for (Node node : allDependenciesNodes) {
            if (node.getGroupId().equals(groupId) && node.getArtifactId().equals(artifactId) && node.getVersion().equals(version)) {
                return node.getChildNodes().size();
            }
        }
        return 0; // should never be reached
    }

    public int getLevel(String groupId, String artifactId, String version) {
        List<Node> allDependenciesNodes = returnAllNodes(rootNode);
        for (Node node : allDependenciesNodes) {
            if (node.getGroupId().equals(groupId) && node.getArtifactId().equals(artifactId) && node.getVersion().equals(version)) {
                return distanceFromRoot(node);
            }
        }
        return 0; // should never be reached
    }

    /**
     * Compute the height of the tree.
     */
    public int heightOfDependencyTree() {
        return heightOfDependencyTree(rootNode) - 1;
    }

    //-------------------------------/
    //------- GETTER METHOD/S -------/
    //-------------------------------/

    public Node getRootNode() {
        return rootNode;
    }

    public int getNbConflictsAvoidedWithDebloating() {
        return nbConflictsAvoidedWithDebloating;
    }

    //-------------------------------/
    //------ PRIVATE METHOD/S -------/
    //-------------------------------/

    private int heightOfDependencyTree(Node node) {
        int h = 0;
        for (int i = 0; i < node.getChildNodes().size(); i++) {
            int k = heightOfDependencyTree(node.getChildNodes().get(i));
            if (k > h)
                h = k;
        }
        return h + 1;
    }

    private int distanceFromRoot(Node node) {
        int d = 0;
        while (node.getParent() != null) {
            d++;
            node = node.getParent();
        }
        return d;
    }

    private List<Node> returnAllNodes(Node node) {
        List<Node> listOfNodes = new ArrayList<>();
        addAllNodes(node, listOfNodes);
        return listOfNodes;
    }

    private static void addAllNodes(Node node, List<Node> listOfNodes) {
        if (node != null) {
            listOfNodes.add(node);
            List<Node> children = node.getChildNodes();
            if (children != null) {
                for (Node child : children) {
                    addAllNodes(child, listOfNodes);
                }
            }
        }
    }

    private void labelNodes(Set<Artifact> usedDeclaredDependencies, Set<Artifact> usedUndeclaredDependencies, Node node) {
        containsUsedArtifact(usedDeclaredDependencies, node);
        containsUsedArtifact(usedUndeclaredDependencies, node);
        for (int i = 0; i < node.getChildNodes().size(); i++) {
            labelNodes(usedDeclaredDependencies, usedUndeclaredDependencies, node.getChildNodes().get(i));
        }
    }

    private void containsUsedArtifact(Set<Artifact> usedArtifacts, Node node) {
        for (Artifact usedUndeclaredDependency : usedArtifacts) {
            String coordinates = usedUndeclaredDependency.getGroupId() + ":" + usedUndeclaredDependency.getArtifactId() + ":" + usedUndeclaredDependency.getVersion();
            if ((node.getGroupId() + ":" + node.getArtifactId() + ":" + node.getVersion()).equals(coordinates)) {
                node.setUsed(true);
                break;
            }
        }
    }

    private boolean canBeRemoved(Node node) {
        List<Node> childNodes = returnAllNodes(node);
        for (Node childNode : childNodes) {
            if (childNode.isUsed() || !childNode.getScope().equals("compile")) { // do not remove direct dependencies that have used children or do not have compile scope
                return false;
            }
        }
        return true;
    }

    private void countConflictsAvoided(Node node) {
        List<Node> childNodes = returnAllNodes(node);
        for (Node childNode : childNodes) {
            if (childNode.isInConflict()) {
                nbConflictsAvoidedWithDebloating++;
            }
        }
    }
}
