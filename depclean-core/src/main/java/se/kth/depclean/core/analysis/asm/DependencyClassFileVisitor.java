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

package se.kth.depclean.core.analysis.asm;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.signature.SignatureVisitor;
import se.kth.depclean.core.analysis.ClassFileVisitor;
import se.kth.depclean.core.analysis.graph.DefaultCallGraph;

/**
 * Computes the set of classes referenced by visited class files, using
 * <a href="DependencyVisitor.html">DependencyVisitor</a>.
 *
 * @see #getDependencies()
 */
@Slf4j
public class DependencyClassFileVisitor implements ClassFileVisitor {

  private final ResultCollector resultCollector = new ResultCollector();

  /**
   * Visit a class file.
   *
   * @see org.apache.invoke.shared.dependency.analyzer.ClassFileVisitor#visitClass(java.lang.String.java.io.InputStream)
   */
  @Override
  public void visitClass(String className, InputStream in) {
    try {
      ClassReader reader = new ClassReader(in);

      final Set<String> constantPoolClassRefs = ConstantPoolParser.getConstantPoolClassReferences(reader.b);
      for (String string : constantPoolClassRefs) {
        resultCollector.addName(string);
      }

      /* visit class members */
      AnnotationVisitor annotationVisitor = new DefaultAnnotationVisitor(
          resultCollector
      );
      SignatureVisitor signatureVisitor = new DefaultSignatureVisitor(
          resultCollector
      );
      FieldVisitor fieldVisitor = new DefaultFieldVisitor(
          annotationVisitor,
          resultCollector
      );
      MethodVisitor methodVisitor = new DefaultMethodVisitor(
          annotationVisitor,
          signatureVisitor,
          resultCollector
      );
      DefaultClassVisitor defaultClassVisitor = new DefaultClassVisitor(
          signatureVisitor,
          annotationVisitor,
          fieldVisitor,
          methodVisitor,
          resultCollector
      );

      reader.accept(defaultClassVisitor, 0);

      // inset edge in the graph based on the bytecode analysis
      //System.out.println("Edge " + className + " -> " + resultCollector.getDependencies());
      DefaultCallGraph.addEdge(className, resultCollector.getDependencies());

    } catch (IndexOutOfBoundsException | IOException e) {
      // some bug inside ASM causes an IOB exception. Log it and move on?
      // this happens when the class isn't valid.
      log.warn("Unable to process: " + className);
    }
    resultCollector.clearClasses();
  }

  // public methods ---------------------------------------------------------

  /**
   * Getter.
   *
   * @return the set of classes referenced by visited class files
   */
  public Set<String> getDependencies() {
    return resultCollector.getDependencies();
  }
}
