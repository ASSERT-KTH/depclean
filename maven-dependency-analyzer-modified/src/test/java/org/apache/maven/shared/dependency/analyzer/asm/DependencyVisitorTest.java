package org.apache.maven.shared.dependency.analyzer.asm;

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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;

import org.objectweb.asm.*;
import org.objectweb.asm.signature.SignatureVisitor;

/**
 * Tests <code>DependencyVisitor</code>.
 * 
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 */
public class DependencyVisitorTest
    extends TestCase
{
    private ResultCollector resultCollector = new ResultCollector();
    private DefaultClassVisitor classVisitor;
    private DefaultClassVisitor visitor;
    private AnnotationVisitor annotationVisitor;
    private SignatureVisitor signatureVisitor;
    private FieldVisitor fieldVisitor;
    private MethodVisitor mv;

    // TestCase methods -------------------------------------------------------

    /*
     * @see junit.framework.TestCase#setUp()
     */
    protected void setUp()
            throws Exception
    {
        annotationVisitor = new DefaultAnnotationVisitor(resultCollector);
        signatureVisitor = new DefaultSignatureVisitor(resultCollector);
        fieldVisitor = new DefaultFieldVisitor(annotationVisitor, resultCollector);
        mv = new DefaultMethodVisitor(annotationVisitor, signatureVisitor, resultCollector);
        visitor = classVisitor = new DefaultClassVisitor(signatureVisitor, annotationVisitor,
                fieldVisitor, mv, resultCollector);
    }

    // visit tests ------------------------------------------------------------

    public void testVisitWithDefaultSuperclass()
    {
        // class a.b.c
        classVisitor.visit( 50, 0, "a/b/c", null, "java/lang/Object", null );

        assertClasses( "java.lang.Object" );
    }

    public void testVisitWithSuperclass()
    {
        // class a.b.c
        classVisitor.visit( 50, 0, "a/b/c", null, "x/y/z", null );

        assertClasses( "x.y.z" );
    }

    public void testVisitWithInterface()
    {
        // class a.b.c implements x.y.z
        classVisitor.visit( 50, 0, "a/b/c", null, "java/lang/Object", new String[] { "x/y/z" } );

        assertClasses( "java.lang.Object", "x.y.z" );
    }

    public void testVisitWithInterfaces()
    {
        // class a.b.c implements p.q.r, x.y.z
        classVisitor.visit( 50, 0, "a/b/c", null, "java/lang/Object", new String[] { "p/q/r", "x/y/z" } );

        assertClasses( "java.lang.Object", "p.q.r", "x.y.z" );
    }

    public void testVisitWithUnboundedClassTypeParameter()
    {
        // class a.b.c<T>
        String signature = "<T:Ljava/lang/Object;>Ljava/lang/Object;";

        visitor.visit( 50, 0, "a/b/c", signature, "java/lang/Object", null );

        assertClasses( "java.lang.Object" );
    }

    public void testVisitWithBoundedClassTypeParameter()
    {
        // class a.b.c<T extends x.y.z>
        String signature = "<T:Lx/y/z;>Ljava/lang/Object;";

        visitor.visit( 50, 0, "a/b/c", signature, "java/lang/Object", null );

        assertClasses( "java.lang.Object", "x.y.z" );
    }

    public void testVisitWithBoundedClassTypeParameters()
    {
        // class a.b.c<K extends p.q.r, V extends x.y.z>
        String signature = "<K:Lp/q/r;V:Lx/y/z;>Ljava/lang/Object;";

        visitor.visit( 50, 0, "a/b/c", signature, "java/lang/Object", null );

        assertClasses( "java.lang.Object", "p.q.r", "x.y.z" );
    }

    public void testVisitWithGenericInterface()
    {
        // class a.b.c implements p.q.r<x.y.z>
        String signature = "Ljava/lang/Object;Lp/q/r<Lx/y/z;>;";

        visitor.visit( 50, 0, "a/b/c", signature, "java/lang/Object", new String[] { "p.q.r" } );

        assertClasses( "java.lang.Object", "p.q.r", "x.y.z" );
    }

    public void testVisitWithInterfaceBound()
    {
        // class a.b.c<T> implements x.y.z<T>
        String signature = "<T:Ljava/lang/Object;>Ljava/lang/Object;Lx/y/z<TT;>;";

        visitor.visit( 50, 0, "a/b/c", signature, "java/lang/Object", new String[] { "x.y.z" } );

        assertClasses( "java.lang.Object", "x.y.z" );
    }

    // visitSource tests ------------------------------------------------------

    public void testVisitSource()
    {
        visitor.visitSource( null, null );

        assertNoClasses();
    }

    // visitOuterClass tests --------------------------------------------------

    public void testVisitOuterClass()
    {
        // class a.b.c
        // {
        //     class ...
        //     {
        //     }
        // }
        visitor.visitOuterClass( "a/b/c", null, null );

        assertNoClasses();
    }

    public void testVisitOuterClassInMethod()
    {
        // class a.b.c
        // {
        //     x.y.z x(p.q.r p)
        //     {
        //         class ...
        //         {
        //         }
        //     }
        // }
        visitor.visitOuterClass( "a/b/c", "x", "(Lp/q/r;)Lx/y/z;" );

        assertNoClasses();
    }

    // visitAnnotation tests --------------------------------------------------

    public void testVisitAnnotation()
    {
        assertVisitor( visitor.visitAnnotation( "La/b/c;", false ) );

        assertClasses( "a.b.c" );
    }

    public void testVisitAnnotationWithRuntimeVisibility()
    {
        assertVisitor( visitor.visitAnnotation( "La/b/c;", true ) );

        assertClasses( "a.b.c" );
    }

    // visitAttribute tests ---------------------------------------------------

    public void testVisitAttribute()
    {
        visitor.visitAttribute( new MockAttribute( "a" ) );

        assertNoClasses();
    }

    // visitInnerClass tests --------------------------------------------------

    public void testVisitInnerClass()
    {
        // TODO: ensure innerName is correct

        // class a.b.c { class x.y.z { } }
        visitor.visitInnerClass( "x/y/z", "a/b/c", "z", 0 );

        assertNoClasses();
    }

    public void testVisitInnerClassAnonymous()
    {
        // class a.b.c { new class x.y.z { } }
        visitor.visitInnerClass( "x/y/z$1", "a/b/c", null, 0 );

        assertNoClasses();
    }

    // visitField tests -------------------------------------------------------

    public void testVisitField()
    {
        // a.b.c a
        assertVisitor( visitor.visitField( 0, "a", "La/b/c;", null, null ) );

        assertClasses( "a.b.c" );
    }

    // TODO: determine actual use of default values
    // public void testVisitFieldWithValue()
    // {
    // }

    public void testVisitFieldArray()
    {
        // a.b.c[] a
        assertVisitor( visitor.visitField( 0, "a", "[La/b/c;", null, null ) );

        assertClasses( "a.b.c" );
    }

    public void testVisitFieldGeneric()
    {
        // a.b.c<x.y.z> a
        assertVisitor( visitor.visitField( 0, "a", "La/b/c;", "La/b/c<Lx/y/z;>;", null ) );

        assertClasses( "a.b.c", "x.y.z" );
    }

    // visitMethod tests ------------------------------------------------------

    public void testVisitMethod()
    {
        // void a()
        assertVisitor( visitor.visitMethod( 0, "a", "()V", null, null ) );

        assertNoClasses();
    }

    public void testVisitMethodWithPrimitiveArgument()
    {
        // void a(int)
        assertVisitor( visitor.visitMethod( 0, "a", "(I)V", null, null ) );

        assertNoClasses();
    }

    public void testVisitMethodWithPrimitiveArrayArgument()
    {
        // void a(int[])
        assertVisitor( visitor.visitMethod( 0, "a", "([I)V", null, null ) );

        assertNoClasses();
    }

    public void testVisitMethodWithObjectArgument()
    {
        // void a(a.b.c)
        assertVisitor( visitor.visitMethod( 0, "a", "(La/b/c;)V", null, null ) );

        assertClasses( "a.b.c" );
    }

    public void testVisitMethodWithObjectArguments()
    {
        // void a(a.b.c, x.y.z)
        assertVisitor( visitor.visitMethod( 0, "a", "(La/b/c;Lx/y/z;)V", null, null ) );

        assertClasses( "a.b.c", "x.y.z" );
    }

    public void testVisitMethodWithObjectArrayArgument()
    {
        // void a(a.b.c[])
        assertVisitor( visitor.visitMethod( 0, "a", "([La/b/c;)V", null, null ) );

        assertClasses( "a.b.c" );
    }

    public void testVisitMethodWithGenericArgument()
    {
        // void a(a.b.c<x.y.z>)
        assertVisitor( visitor.visitMethod( 0, "a", "(La/b/c;)V", "(La/b/c<Lx/y/z;>;)V", null ) );

        assertClasses( "a.b.c", "x.y.z" );
    }

    public void testVisitMethodWithPrimitiveReturnType()
    {
        // int a()
        assertVisitor( visitor.visitMethod( 0, "a", "()I", null, null ) );

        assertNoClasses();
    }

    public void testVisitMethodWithPrimitiveArrayReturnType()
    {
        // int[] a()
        assertVisitor( visitor.visitMethod( 0, "a", "()[I", null, null ) );

        assertNoClasses();
    }

    public void testVisitMethodWithObjectReturnType()
    {
        // a.b.c a()
        assertVisitor( visitor.visitMethod( 0, "a", "()La/b/c;", null, null ) );

        assertClasses( "a.b.c" );
    }

    public void testVisitMethodWithObjectArrayReturnType()
    {
        // a.b.c[] a()
        assertVisitor( visitor.visitMethod( 0, "a", "()[La/b/c;", null, null ) );

        assertClasses( "a.b.c" );
    }

    public void testVisitMethodWithException()
    {
        // void a() throws a.b.c
        assertVisitor( visitor.visitMethod( 0, "a", "()V", null, new String[] { "a/b/c" } ) );

        assertClasses( "a.b.c" );
    }

    public void testVisitMethodWithExceptions()
    {
        // void a() throws a.b.c, x.y.z
        assertVisitor( visitor.visitMethod( 0, "a", "()V", null, new String[] { "a/b/c", "x/y/z" } ) );

        assertClasses( "a.b.c", "x.y.z" );
    }

    // visitAnnotationDefault tests -------------------------------------------

    public void testVisitAnnotationDefault()
    {
        assertVisitor( mv.visitAnnotationDefault() );
        assertNoClasses();
    }

    // visitParameterAnnotation tests -------------------------------------------

    public void testVisitParameterAnnotation()
    {
        // @a.b.c
        assertVisitor( mv.visitParameterAnnotation( 0, "La/b/c;", false ) );

        assertClasses( "a.b.c" );
    }

    // visitCode tests --------------------------------------------------------

    public void testVisitCode()
    {
        mv.visitCode();

        assertNoClasses();
    }

    // visitFrame tests -------------------------------------------------------

    public void testVisitFrame()
    {
        mv.visitFrame( Opcodes.F_NEW, 0, new Object[0], 0, new Object[0] );

        assertNoClasses();
    }

    // visitInsn tests --------------------------------------------------------

    public void testVisitInsn()
    {
        mv.visitInsn( Opcodes.NOP );

        assertNoClasses();
    }

    // visitIntInsn tests -----------------------------------------------------

    public void testVisitIntInsn()
    {
        mv.visitIntInsn( Opcodes.BIPUSH, 0 );

        assertNoClasses();
    }

    // visitVarInsn tests -----------------------------------------------------

    public void testVisitVarInsn()
    {
        mv.visitVarInsn( Opcodes.ILOAD, 0 );

        assertNoClasses();
    }

    // visitTypeInsn tests ----------------------------------------------------

    public void testVisitTypeInsn()
    {
        mv.visitTypeInsn( Opcodes.NEW, "a/b/c" );

        assertClasses( "a.b.c" );
    }

    // visitFieldInsn tests ---------------------------------------------------

    public void testVisitFieldInsnWithPrimitive()
    {
        mv.visitFieldInsn( Opcodes.GETFIELD, "a/b/c", "x", "I" );

        assertClasses( "a.b.c" );
    }

    public void testVisitFieldInsnWithObject()
    {
        mv.visitFieldInsn( Opcodes.GETFIELD, "a/b/c", "x", "Lx/y/z;" );

        assertClasses( "a.b.c" );
    }

    // visitMethodInsn tests --------------------------------------------------

    public void testVisitMethodInsn()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()V", false );

        assertClasses( "a.b.c" );
    }

    public void testVisitMethodInsnWithPrimitiveArgument()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "(I)V", false );

        assertClasses( "a.b.c" );
    }

    public void testVisitMethodInsnWithPrimitiveArrayArgument()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "([I)V", false );

        assertClasses( "a.b.c" );
    }

    public void testVisitMethodInsnWithObjectArgument()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "(Lx/y/z;)V", false );

        assertClasses( "a.b.c" );
    }

    public void testVisitMethodInsnWithObjectArguments()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "(Lp/q/r;Lx/y/z;)V", false );

        assertClasses( "a.b.c" );
    }

    public void testVisitMethodInsnWithObjectArrayArgument()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "([Lx/y/z;)V", false );

        assertClasses( "a.b.c" );
    }

    public void testVisitMethodInsnWithPrimitiveReturnType()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()I", false );

        assertClasses( "a.b.c" );
    }

    public void testVisitMethodInsnWithPrimitiveArrayReturnType()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()[I", false );

        assertClasses( "a.b.c" );
    }

    public void testVisitMethodInsnWithObjectReturnType()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()Lx/y/z;", false );

        assertClasses( "a.b.c" );
    }

    public void testVisitMethodInsnWithObjectArrayReturnType()
    {
        mv.visitMethodInsn( Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()[Lx/y/z;", false );

        assertClasses( "a.b.c" );
    }

    // visitJumpInsn tests ----------------------------------------------------

    public void testVisitJumpInsn()
    {
        mv.visitJumpInsn( Opcodes.IFEQ, new Label() );

        assertNoClasses();
    }

    // visitLabel tests -------------------------------------------------------

    public void testVisitLabel()
    {
        mv.visitLabel( new Label() );

        assertNoClasses();
    }

    // visitLdcInsn tests -----------------------------------------------------

    public void testVisitLdcInsnWithNonType()
    {
        mv.visitLdcInsn( "a" );

        assertNoClasses();
    }

    public void testVisitLdcInsnWithPrimitiveType()
    {
        mv.visitLdcInsn( Type.INT_TYPE );

        assertNoClasses();
    }

    public void testVisitLdcInsnWithObjectType()
    {
        mv.visitLdcInsn( Type.getType( "La/b/c;" ) );

        assertClasses( "a.b.c" );
    }

    // visitIincInsn tests ----------------------------------------------------

    public void testVisitIincInsn()
    {
        mv.visitIincInsn( 0, 1 );

        assertNoClasses();
    }

    // visitTableSwitchInsn tests ---------------------------------------------

    public void testVisitTableSwitchInsn()
    {
        mv.visitTableSwitchInsn( 0, 1, new Label(), new Label[] { new Label() } );

        assertNoClasses();
    }

    // visitLookupSwitchInsn tests --------------------------------------------

    public void testVisitLookupSwitchInsn()
    {
        mv.visitLookupSwitchInsn( new Label(), new int[] { 0 }, new Label[] { new Label() } );

        assertNoClasses();
    }

    // visitMultiANewArrayInsn tests ------------------------------------------

    public void testVisitMultiANewArrayInsnWithPrimitive()
    {
        mv.visitMultiANewArrayInsn( "I", 2 );

        assertNoClasses();
    }

    public void testVisitMultiANewArrayInsnWithObject()
    {
        mv.visitMultiANewArrayInsn( "La/b/c;", 2 );

        assertClasses( "a.b.c" );
    }

    // visitTryCatchBlock tests -----------------------------------------------

    public void testVisitTryCatchBlock()
    {
        mv.visitTryCatchBlock( new Label(), new Label(), new Label(), "a/b/c" );

        assertClasses( "a.b.c" );
    }

    public void testVisitTryCatchBlockForFinally()
    {
        mv.visitTryCatchBlock( new Label(), new Label(), new Label(), null );

        assertNoClasses();
    }

    // visitLocalVariable tests -----------------------------------------------

    public void testVisitLocalVariableWithPrimitive()
    {
        mv.visitLocalVariable( "a", "I", null, new Label(), new Label(), 0 );

        assertNoClasses();
    }

    public void testVisitLocalVariableWithPrimitiveArray()
    {
        mv.visitLocalVariable( "a", "[I", null, new Label(), new Label(), 0 );

        assertNoClasses();
    }

    public void testVisitLocalVariableWithObject()
    {
        mv.visitLocalVariable( "a", "La/b/c;", null, new Label(), new Label(), 0 );

        assertClasses( "a.b.c" );
    }

    public void testVisitLocalVariableWithObjectArray()
    {
        mv.visitLocalVariable( "a", "[La/b/c;", null, new Label(), new Label(), 0 );

        assertClasses( "a.b.c" );
    }

    public void testVisitLocalVariableWithGenericObject()
    {
        mv.visitLocalVariable( "a", "La/b/c;", "La/b/c<Lx/y/z;>;", new Label(), new Label(), 0 );

        assertClasses( "a.b.c", "x.y.z" );
    }

    public void testVisitLocalVariableWithGenericObjectArray()
    {
        mv.visitLocalVariable( "a", "La/b/c;", "[La/b/c<Lx/y/z;>;", new Label(), new Label(), 0 );

        assertClasses( "a.b.c", "x.y.z" );
    }

    // visitLineNumber tests --------------------------------------------------

    public void testVisitLineNumber()
    {
        mv.visitLineNumber( 0, new Label() );

        assertNoClasses();
    }

    // visitMaxs tests --------------------------------------------------------

    public void testVisitMaxs()
    {
        mv.visitMaxs( 0, 0 );

        assertNoClasses();
    }

    // private methods --------------------------------------------------------

    private void assertVisitor( Object actualVisitor )
    {
        //assertEquals( visitor, actualVisitor );
    }

    private void assertNoClasses()
    {
        assertClasses( Collections.<String>emptySet() );
    }

    private void assertClasses( String element )
    {
        assertClasses( Collections.singleton( element ) );
    }

    private void assertClasses( String expectedClass1, String expectedClass2 )
    {
        assertClasses( new String[] { expectedClass1, expectedClass2 } );
    }

    private void assertClasses( String expectedClass1, String expectedClass2, String expectedClass3 )
    {
        assertClasses( new String[] { expectedClass1, expectedClass2, expectedClass3 } );
    }

    private void assertClasses( String[] expectedClasses )
    {
        assertClasses( new HashSet<String>( Arrays.asList( expectedClasses ) ) );
    }

    private void assertClasses( Set<String> expectedClasses )
    {
        assertEquals( expectedClasses, resultCollector.getDependencies() );
    }
}
