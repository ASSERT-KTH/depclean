/*
 * Copyright (c) 2020, CASTOR Software Research Centre (www.castor.kth.se)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met: 1) Redistributions of source code must retain the above
 * copyright notice, this list of conditions and the following
 * disclaimer. 2) Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided
 * with the distribution. 3) Neither the name of the Qulice.com nor
 * the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package se.kth.depclean.core.analysis.graph;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.jgrapht.graph.AbstractBaseGraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.DepthFirstIterator;

public class DefaultCallGraph
{
   private static AbstractBaseGraph<String, DefaultEdge> directedGraph =
      new DefaultDirectedGraph<>(DefaultEdge.class);

   private static Set<String> projectVertices = new HashSet<>();

   public static void addEdge(String clazz, Set<String> referencedClassMembers)
   {
      directedGraph.addVertex(clazz);
      for (String referencedClassMember : referencedClassMembers) {
         if (!directedGraph.containsVertex(referencedClassMember)) {
            directedGraph.addVertex(referencedClassMember);
         }
         directedGraph.addEdge(clazz, referencedClassMember);
         projectVertices.add(clazz);

         //            System.out.println(clazz + " -> " + referencedClassMember);
      }
   }

   public static Set<String> referencedClassMembers(Set<String> projectClasses)
   {
      //        System.out.println("project classes: " + projectClasses);
      Set<String> allReferencedClassMembers = new HashSet<>();
      for (String projectClass : projectClasses) {
         allReferencedClassMembers.addAll(traverse(projectClass));
      }
      return allReferencedClassMembers;
   }

   public static Set<String> getProjectVertices()
   {
      return projectVertices;
   }

   public static Set<String> getVertices()
   {
      return directedGraph.vertexSet();
   }

   public static void cleanDirectedGraph()
   {
      directedGraph.vertexSet().clear();
      directedGraph.edgeSet().clear();
   }

   private static Set<String> traverse(String start)
   {
      Set<String> referencedClassMembers = new HashSet<>();
      Iterator<String> iterator = new DepthFirstIterator<>(directedGraph, start);
      while (iterator.hasNext()) {
         referencedClassMembers.add(iterator.next());
      }
      return referencedClassMembers;
   }

   public AbstractBaseGraph<String, DefaultEdge> getDirectedGraph()
   {
      return directedGraph;
   }
}

