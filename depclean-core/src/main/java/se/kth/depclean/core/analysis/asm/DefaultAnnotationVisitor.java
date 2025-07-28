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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Computes the set of classes referenced by visited code. Inspired by
 * <code>org.objectweb.asm.depend.DependencyVisitor</code> in the ASM dependencies example.
 */
public class DefaultAnnotationVisitor extends AnnotationVisitor {

  private final ResultCollector resultCollector;

  public DefaultAnnotationVisitor(@NonNull ResultCollector resultCollector) {
    super(Opcodes.ASM9);
    this.resultCollector = resultCollector;
  }

  @Override
  public void visit(@Nullable final String name, @Nullable final Object value) {
    if (value instanceof Type type) {
      resultCollector.addType(type);
    }
  }

  @Override
  public void visitEnum(@Nullable final String name, @NonNull final String desc, @Nullable final String value) {
    resultCollector.addDesc(desc);
  }

  @Override
  @Nullable
  public AnnotationVisitor visitAnnotation(@Nullable final String name, @NonNull final String desc) {
    resultCollector.addDesc(desc);
    return this;
  }

  /*
   * @see org.objectweb.asm.AnnotationVisitor#visitArray(java.lang.String)
   */
  @Override
  @Nullable
  public AnnotationVisitor visitArray(@Nullable final String name) {
    return this;
  }
}
