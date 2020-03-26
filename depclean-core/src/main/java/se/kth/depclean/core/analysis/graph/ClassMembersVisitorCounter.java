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

public final class ClassMembersVisitorCounter
{
   /**
    * Number of types.
    */
   private static long nbVisitedTypes;

   /**
    * Number of fields.
    */
   private static long nbVisitedFields;

   /**
    * Number of methods.
    */
   private static long nbVisitedMethods;

   /**
    * Number of annotations.
    */
   private static long nbVisitedAnnotations;

   private ClassMembersVisitorCounter()
   {
      throw new IllegalStateException("Utility class");
   }

   public static void resetClassCounters()
   {
      nbVisitedTypes = 0;
      nbVisitedFields = 0;
      nbVisitedMethods = 0;
      nbVisitedAnnotations = 0;
   }

   public static void markAsNotFoundClassCounters()
   {
      nbVisitedTypes = -1;
      nbVisitedFields = -1;
      nbVisitedMethods = -1;
      nbVisitedAnnotations = -1;
   }

   public static void addVisitedClass()
   {
      nbVisitedTypes++;
   }

   public static void addVisitedField()
   {
      nbVisitedFields++;
   }

   public static void addVisitedMethod()
   {
      nbVisitedMethods++;
   }

   public static void addVisitedAnnotation()
   {
      nbVisitedAnnotations++;
   }

   public static long getNbVisitedTypes()
   {
      return nbVisitedTypes;
   }

   public static long getNbVisitedFields()
   {
      return nbVisitedFields;
   }

   public static long getNbVisitedMethods()
   {
      return nbVisitedMethods;
   }

   public static long getNbVisitedAnnotations()
   {
      return nbVisitedAnnotations;
   }

}
