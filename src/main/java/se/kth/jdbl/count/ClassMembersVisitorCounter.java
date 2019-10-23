package se.kth.jdbl.count;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

public class ClassMembersVisitorCounter {

    //--------------------------/
    //------ CLASS FIELDS ------/
    //--------------------------/

    private static long nbVisitedTypes;
    private static long nbVisitedFields;
    private static long nbVisitedMethods;
    private static long nbVisitedAnnotations;

    //--------------------------/
    //------ CONSTRUCTORS ------/
    //--------------------------/

    private ClassMembersVisitorCounter() {
        throw new IllegalStateException("Utility class");
    }

    //--------------------------/
    //----- PUBLIC METHODS -----/
    //--------------------------/

    public static void resetClassCounters() {
        nbVisitedTypes = 0;
        nbVisitedFields = 0;
        nbVisitedMethods = 0;
        nbVisitedAnnotations = 0;
    }

    public static void markAsNotFoundClassCounters() {
        nbVisitedTypes = -1;
        nbVisitedFields = -1;
        nbVisitedMethods = -1;
        nbVisitedAnnotations = -1;
    }

    public static void addVisitedClass() {
        nbVisitedTypes++;
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

    public static long getNbVisitedTypes() {
        return nbVisitedTypes;
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