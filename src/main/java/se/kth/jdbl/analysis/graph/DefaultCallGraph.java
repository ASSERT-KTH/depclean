package se.kth.jdbl.analysis.graph;

import org.jgrapht.graph.AbstractBaseGraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.DepthFirstIterator;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class DefaultCallGraph {

    //--------------------------------/
    //-------- CLASS FIELD/S --------/
    //------------------------------/

    private static AbstractBaseGraph<String, DefaultEdge> directedGraph = new DefaultDirectedGraph<>(DefaultEdge.class);

    //--------------------------------/
    //------- PUBLIC METHOD/S -------/
    //------------------------------/

    public static void addEdge(String clazz, Set<String> referencedClassMembers) {
        directedGraph.addVertex(clazz);
        for (String referencedClassMember : referencedClassMembers) {
            if (!directedGraph.containsVertex(referencedClassMember)) {
                directedGraph.addVertex(referencedClassMember);
            }
            directedGraph.addEdge(clazz, referencedClassMember);

            System.out.println(clazz + " -> " + referencedClassMember);
        }
    }

    public static Set<String> referencedClassMembers(Set<String> projectClasses) {
        System.out.println("project classes: " + projectClasses);
        Set<String> allReferencedClassMembers = new HashSet<>();
        for (String projectClass : projectClasses) {
            allReferencedClassMembers.addAll(traverse(projectClass));
        }
        return allReferencedClassMembers;
    }

    public static void cleanDirectedGraph(){
        directedGraph.vertexSet().clear();
        directedGraph.edgeSet().clear();
    }

    public AbstractBaseGraph<String, DefaultEdge> getDirectedGraph() {
        return directedGraph;
    }

    //--------------------------------/
    //------ PRIVATE METHOD/S -------/
    //------------------------------/

    private static Set<String> traverse(String start) {
        Set<String> referencedClassMembers = new HashSet<>();
        Iterator<String> iterator = new DepthFirstIterator<>(directedGraph, start);
        while (iterator.hasNext()) {
            referencedClassMembers.add(iterator.next());
        }
        return referencedClassMembers;
    }
}

