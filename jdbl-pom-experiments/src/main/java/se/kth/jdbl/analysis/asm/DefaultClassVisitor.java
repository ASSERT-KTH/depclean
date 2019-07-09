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

import org.objectweb.asm.*;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import se.kth.jdbl.counter.ClassMembersVisitorCounter;

/**
 * Computes the set of classes referenced by visited code.
 * Inspired by <code>org.objectweb.asm.depend.DependencyVisitor</code> in the ASM dependencies example.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 */
public class DefaultClassVisitor
        extends ClassVisitor {
    // fields -----------------------------------------------------------------

    private final ResultCollector resultCollector;

    private final SignatureVisitor signatureVisitor;

    private final AnnotationVisitor annotationVisitor;

    private final FieldVisitor fieldVisitor;

    private final MethodVisitor methodVisitor;


    // constructors -----------------------------------------------------------

    public DefaultClassVisitor(SignatureVisitor signatureVisitor, AnnotationVisitor annotationVisitor,
                               FieldVisitor fieldVisitor, MethodVisitor methodVisitor,
                               ResultCollector resultCollector) {
        super(Opcodes.ASM7);
        this.signatureVisitor = signatureVisitor;
        this.annotationVisitor = annotationVisitor;
        this.fieldVisitor = fieldVisitor;
        this.methodVisitor = methodVisitor;
        this.resultCollector = resultCollector;
    }

    public void visit(final int version, final int access, final String name, final String signature,
                      final String superName, final String[] interfaces) {
//        System.out.println("visiting class: " +  name);
        ClassMembersVisitorCounter.addVisitedClass();
        if (signature == null) {

            resultCollector.addName(superName);
            resultCollector.addNames(interfaces);
        } else {
            addSignature(signature);
        }
    }

    public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
//        System.out.println("\t" + "visiting annotation: " +  desc);
        ClassMembersVisitorCounter.addVisitedAnnotation();
        resultCollector.addDesc(desc);

        return annotationVisitor;
    }

    public FieldVisitor visitField(final int access, final String name, final String desc, final String signature,
                                   final Object value) {
//        System.out.println("\t" + "visiting field: " +  name);
        ClassMembersVisitorCounter.addVisitedField();
        if (signature == null) {
            resultCollector.addDesc(desc);
        } else {
            addTypeSignature(signature);
        }

        if (value instanceof Type) {
            resultCollector.addType((Type) value);
        }

        return fieldVisitor;
    }

    public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature,
                                     final String[] exceptions) {
//        System.out.println("\t" + "visiting method: " +  name);
        ClassMembersVisitorCounter.addVisitedMethod();
        if (signature == null) {
            resultCollector.addMethodDesc(desc);
        } else {
            addSignature(signature);
        }

        resultCollector.addNames(exceptions);

        return methodVisitor;
    }

    public void visitNestHost(final String nestHost) {
        resultCollector.addName(nestHost);
    }

    public void visitNestMember(final String nestMember) {
        resultCollector.addName(nestMember);
    }

    // private methods --------------------------------------------------------

    private void addSignature(final String signature) {
        if (signature != null) {
            new SignatureReader(signature).accept(signatureVisitor);
        }
    }

    private void addTypeSignature(final String signature) {
        if (signature != null) {
            new SignatureReader(signature).acceptType(signatureVisitor);
        }
    }

}
