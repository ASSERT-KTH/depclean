package se.kth.jdbl.pom;

import fr.dutra.tools.maven.deptree.core.InputType;
import fr.dutra.tools.maven.deptree.core.Node;
import fr.dutra.tools.maven.deptree.core.ParseException;
import fr.dutra.tools.maven.deptree.core.Parser;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class DependencyTreeAnalyzer {

    // fields ---------------------------------------------------------------------------------------------------------

    Node rootNode;

    // constructors ---------------------------------------------------------------------------------------------------

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

    // public methods -------------------------------------------------------------------------------------------------

    public ArrayList<String> getArtifactsAllDependencies(ArrayList<String> artifacts) {
        ArrayList<String> listOfChildrensCanonical = new ArrayList();
        List<Node> allDependenciesNodes = returnAllNodes(rootNode);
        allDependenciesNodes.remove(rootNode);

        for (Node node : allDependenciesNodes) {
            // if the node is declared but unused
            if (artifacts.contains(node.getArtifactCanonicalForm())) {
                // get all dependencies of the node
                List<Node> allDeps = returnAllNodes(node);
                allDeps.remove(node);
                for (Node dependency : allDeps) {
                    // add the dependencies to the the output
                    listOfChildrensCanonical.add(dependency.getArtifactCanonicalForm());
                }
            }
        }
        return listOfChildrensCanonical;
    }

    public ArrayList<String> getDirectDependencies() {
        List<Node> directDependenciesNodes = rootNode.getChildNodes();
        ArrayList<String> directDependenciesCanonical = new ArrayList();
        for (Node dependency : directDependenciesNodes) {
            directDependenciesCanonical.add(dependency.getArtifactCanonicalForm());
        }
        return directDependenciesCanonical;
    }

    public ArrayList<String> getTransitiveDependencies() {
        ArrayList<String> allDependenciesCanonical = getAllDependencies();
        allDependenciesCanonical.removeAll(getDirectDependencies());
        return allDependenciesCanonical;
    }

    public ArrayList<String> getAllDependencies() {
        List<Node> allDependenciesNodes = returnAllNodes(rootNode);
        allDependenciesNodes.remove(rootNode);
        ArrayList<String> allDependenciesCanonical = new ArrayList();
        for (Node dependency : allDependenciesNodes) {
            allDependenciesCanonical.add(dependency.getArtifactCanonicalForm());
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

    // private methods -------------------------------------------------------------------------------------------------

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
}
