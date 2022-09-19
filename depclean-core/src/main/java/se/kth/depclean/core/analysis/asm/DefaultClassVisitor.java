package se.kth.depclean.core.analysis.asm;

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
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import se.kth.depclean.core.analysis.graph.ClassMembersVisitorCounter;

/**
 * Computes the set of classes referenced by visited code. Inspired by
 * <code>org.objectweb.asm.depend.DependencyVisitor</code> in the ASM dependencies example.
 */
public class DefaultClassVisitor extends ClassVisitor {

  private final ResultCollector resultCollector;
  private final SignatureVisitor signatureVisitor;
  private final AnnotationVisitor annotationVisitor;
  private final FieldVisitor fieldVisitor;
  private final MethodVisitor methodVisitor;

  /**
   * Ctor.
   */
  public DefaultClassVisitor(SignatureVisitor signatureVisitor,
      AnnotationVisitor annotationVisitor,
      FieldVisitor fieldVisitor,
      MethodVisitor methodVisitor,
      ResultCollector resultCollector) {
    super(Opcodes.ASM9);
    this.signatureVisitor = signatureVisitor;
    this.annotationVisitor = annotationVisitor;
    this.fieldVisitor = fieldVisitor;
    this.methodVisitor = methodVisitor;
    this.resultCollector = resultCollector;
  }

  @Override
  public void visit(final int version, final int access, final String name, final String signature,
      final String superName, final String[] interfaces) {
    //System.out.println("Visiting class: " + name);
    ClassMembersVisitorCounter.addVisitedClass();
    if (signature == null) {
      resultCollector.addName(superName);
      resultCollector.addNames(interfaces);
    } else {
      addSignature(signature);
    }
  }

  @Override
  public void visitNestHost(final String nestHost) {
    resultCollector.addName(nestHost);
  }

  @Override
  public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
    // System.out.println("\t" + "visiting annotation: " + desc);
    ClassMembersVisitorCounter.addVisitedAnnotation();
    resultCollector.addDesc(desc);
    return annotationVisitor;
  }

  @Override
  public void visitNestMember(final String nestMember) {
    resultCollector.addName(nestMember);
  }

  @Override
  public FieldVisitor visitField(final int access, final String name, final String desc, final String signature,
      final Object value) {
    // System.out.println("\t" + "visiting field: " + name);
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

  @Override
  public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature,
      final String[] exceptions) {
    // System.out.println("\t" + "visiting method: " + name);
    ClassMembersVisitorCounter.addVisitedMethod();
    if (signature == null) {
      resultCollector.addMethodDesc(desc);
    } else {
      addSignature(signature);
    }
    resultCollector.addNames(exceptions);
    return methodVisitor;
  }

  // private methods --------------------------------------------------------

  private void addTypeSignature(final String signature) {
    if (signature != null) {
      new SignatureReader(signature).acceptType(signatureVisitor);
    }
  }

  private void addSignature(final String signature) {
    if (signature != null) {
      new SignatureReader(signature).accept(signatureVisitor);
    }
  }
}
