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
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Computes the set of classes referenced by visited code.
 * Inspired by <code>org.objectweb.asm.depend.DependencyVisitor</code> in the ASM dependencies example.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 */
public class DefaultFieldVisitor
        extends FieldVisitor {
    private final AnnotationVisitor annotationVisitor;

    private final ResultCollector resultCollector;

    public DefaultFieldVisitor(AnnotationVisitor annotationVisitor, ResultCollector resultCollector) {
        super(Opcodes.ASM7);
        this.annotationVisitor = annotationVisitor;
        this.resultCollector = resultCollector;
    }

    public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
        resultCollector.addDesc(desc);

        return annotationVisitor;
    }

}
