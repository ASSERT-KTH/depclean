package se.kth.jdbl.analysis.asm;

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

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.signature.SignatureVisitor;
import se.kth.jdbl.analysis.ClassFileVisitor;
import se.kth.jdbl.analysis.graph.DefaultCallGraph;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * Computes the set of classes referenced by visited class files, using
 * <a href="DependencyVisitor.html">DependencyVisitor</a>.
 *
 * @see #getDependencies()
 */
public class DependencyClassFileVisitor implements ClassFileVisitor {
    // fields -----------------------------------------------------------------

    private final ResultCollector resultCollector = new ResultCollector();


    // constructors -----------------------------------------------------------

    public DependencyClassFileVisitor() {
    }

    // ClassFileVisitor methods -----------------------------------------------

    /*
     * @see org.apache.invoke.shared.dependency.analyzer.ClassFileVisitor#visitClass(java.lang.String,
     *      java.io.InputStream)
     */
    public void visitClass(String className, InputStream in) {
        try {
            ClassReader reader = new ClassReader(in);

            System.out.println("**************************************************");
            System.out.println("Reading class: " + className);

            final Set<String> constantPoolClassRefs = ConstantPoolParser.getConstantPoolClassReferences(reader.b);
            for (String string : constantPoolClassRefs) {
                resultCollector.addName(string);
            }

            /* visit class members */
            AnnotationVisitor annotationVisitor = new DefaultAnnotationVisitor(resultCollector);
            SignatureVisitor signatureVisitor = new DefaultSignatureVisitor(resultCollector);
            FieldVisitor fieldVisitor = new DefaultFieldVisitor(annotationVisitor, resultCollector);
            MethodVisitor methodVisitor = new DefaultMethodVisitor(annotationVisitor, signatureVisitor, resultCollector);

            DefaultClassVisitor defaultClassVisitor = new DefaultClassVisitor(signatureVisitor, annotationVisitor, fieldVisitor, methodVisitor, resultCollector);

            reader.accept(defaultClassVisitor, 0);

            // inset edge in the graph based on the bytecode analysis
            DefaultCallGraph.addEdge(className, resultCollector.getDependencies());
            resultCollector.clearClasses();

        } catch (IOException exception) {
            exception.printStackTrace();
        } catch (IndexOutOfBoundsException e) {
            // some bug inside ASM causes an IOB exception. Log it and move on?
            // this happens when the class isn't valid.
            System.out.println("Unable to process: " + className);
        }
    }

    // public methods ---------------------------------------------------------

    /**
     * @return the set of classes referenced by visited class files
     */
    public Set<String> getDependencies() {
        return resultCollector.getDependencies();
    }
}
