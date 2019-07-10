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

/**
 * Computes the set of classes referenced by visited code.
 * Inspired by <code>org.objectweb.asm.depend.DependencyVisitor</code> in the ASM dependencies example.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 */
public class DefaultMethodVisitor
    extends MethodVisitor
{
    private final AnnotationVisitor annotationVisitor;

    private final SignatureVisitor signatureVisitor;

    private final ResultCollector resultCollector;

    public DefaultMethodVisitor( AnnotationVisitor annotationVisitor, SignatureVisitor signatureVisitor,
                                 ResultCollector resultCollector )
    {
        super( Opcodes.ASM7 );
        this.annotationVisitor = annotationVisitor;
        this.signatureVisitor = signatureVisitor;
        this.resultCollector = resultCollector;
    }

    public AnnotationVisitor visitAnnotation( final String desc, final boolean visible )
    {
        resultCollector.addDesc( desc );

        return annotationVisitor;
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation( int typeRef, TypePath typePath, String desc, boolean visible )
    {
        resultCollector.addDesc( desc );

        return annotationVisitor;
    }

    public AnnotationVisitor visitParameterAnnotation( final int parameter, final String desc, final boolean visible )
    {
        resultCollector.addDesc( desc );

        return annotationVisitor;
    }

    @Override
    public AnnotationVisitor visitLocalVariableAnnotation( int typeRef, TypePath typePath, Label[] start, Label[] end,
                                                           int[] index, String desc, boolean visible )
    {
        resultCollector.addDesc( desc );

        return annotationVisitor;
    }

    public void visitTypeInsn( final int opcode, final String desc )
    {
        if ( desc.charAt( 0 ) == '[' )
        {
            resultCollector.addDesc( desc );
        }
        else
        {
            resultCollector.addName( desc );
        }
    }

    public void visitFieldInsn( final int opcode, final String owner, final String name, final String desc )
    {
        resultCollector.addName( owner );
        /*
         * NOTE: Merely accessing a field does not impose a direct dependency on its type. For example, the code line
         * <code>java.lang.Object var = bean.field;</code> does not directly depend on the type of the field. A direct
         * dependency is only introduced when the code explicitly references the field's type by means of a variable
         * declaration or a type check/cast. Those cases are handled by other visitor callbacks.
         */
    }

    @Override
    public void visitMethodInsn( int opcode, String owner, String name, String desc, boolean itf )
    {
        resultCollector.addName( owner );
    }

    public void visitLdcInsn( final Object cst )
    {
        if ( cst instanceof Type )
        {
            resultCollector.addType( (Type) cst );
        }
    }

    public void visitMultiANewArrayInsn( final String desc, final int dims )
    {
        resultCollector.addDesc( desc );
    }

    public void visitTryCatchBlock( final Label start, final Label end, final Label handler, final String type )
    {
        resultCollector.addName( type );
    }

    public void visitLocalVariable( final String name, final String desc, final String signature, final Label start,
                                    final Label end, final int index )
    {
        if ( signature == null )
        {
            resultCollector.addDesc( desc );
        }
        else
        {
            addTypeSignature( signature );
        }
    }



    private void addTypeSignature( final String signature )
    {
        if ( signature != null )
        {
            new SignatureReader( signature ).acceptType( signatureVisitor );
        }
    }
}
