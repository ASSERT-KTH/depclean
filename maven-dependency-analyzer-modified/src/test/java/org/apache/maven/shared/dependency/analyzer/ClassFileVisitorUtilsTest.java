package org.apache.maven.shared.dependency.analyzer;

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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.jar.JarOutputStream;

import org.codehaus.plexus.util.FileUtils;
import org.jmock.Mock;

/**
 * Tests <code>ClassFileVisitorUtils</code>.
 * 
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 * @see ClassFileVisitorUtils
 */
public class ClassFileVisitorUtilsTest
    extends AbstractFileTest
{
    // tests ------------------------------------------------------------------

    public void testAcceptJar()
        throws IOException
    {
        File file = createJar();
        JarOutputStream out = new JarOutputStream( new FileOutputStream( file ) );
        writeEntry( out, "a/b/c.class", "class a.b.c" );
        writeEntry( out, "x/y/z.class", "class x.y.z" );
        out.close();

        Mock mock = mock( ClassFileVisitor.class );
        expectVisitClass( mock, "a.b.c", "class a.b.c" );
        expectVisitClass( mock, "x.y.z", "class x.y.z" );

        ClassFileVisitorUtils.accept( file.toURI().toURL(), (ClassFileVisitor) mock.proxy() );

        mock.verify();
    }

    public void testAcceptJarWithNonClassEntry()
        throws IOException
    {
        File file = createJar();
        JarOutputStream out = new JarOutputStream( new FileOutputStream( file ) );
        writeEntry( out, "a/b/c.jpg", "jpeg a.b.c" );
        out.close();

        Mock mock = mock( ClassFileVisitor.class );

        ClassFileVisitorUtils.accept( file.toURI().toURL(), (ClassFileVisitor) mock.proxy() );

        mock.verify();
    }

    public void testAcceptDir()
        throws IOException
    {
        File dir = createDir();

        File abDir = mkdirs( dir, "a/b" );
        createFile( abDir, "c.class", "class a.b.c" );

        File xyDir = mkdirs( dir, "x/y" );
        createFile( xyDir, "z.class", "class x.y.z" );

        Mock mock = mock( ClassFileVisitor.class );
        expectVisitClass( mock, "a.b.c", "class a.b.c" );
        expectVisitClass( mock, "x.y.z", "class x.y.z" );

        ClassFileVisitorUtils.accept( dir.toURI().toURL(), (ClassFileVisitor) mock.proxy() );

        FileUtils.deleteDirectory( dir );

        mock.verify();
    }

    public void testAcceptDirWithNonClassFile()
        throws IOException
    {
        File dir = createDir();

        File abDir = mkdirs( dir, "a/b" );
        createFile( abDir, "c.jpg", "jpeg a.b.c" );

        Mock mock = mock( ClassFileVisitor.class );

        ClassFileVisitorUtils.accept( dir.toURI().toURL(), (ClassFileVisitor) mock.proxy() );

        FileUtils.deleteDirectory( dir );

        mock.verify();
    }

    public void testAcceptWithFile()
        throws IOException
    {
        File file = File.createTempFile( "test", ".class" );
        file.deleteOnExit();

        Mock mock = mock( ClassFileVisitor.class );

        URL url = file.toURI().toURL();

        try
        {
            ClassFileVisitorUtils.accept( url, (ClassFileVisitor) mock.proxy() );
        }
        catch ( IllegalArgumentException exception )
        {
            assertEquals( "Cannot accept visitor on URL: " + url, exception.getMessage() );
        }
    }

    public void testAcceptWithUnsupportedScheme()
        throws IOException
    {
        Mock mock = mock( ClassFileVisitor.class );

        URL url = new URL( "http://localhost/" );

        try
        {
            ClassFileVisitorUtils.accept( url, (ClassFileVisitor) mock.proxy() );
        }
        catch ( IllegalArgumentException exception )
        {
            assertEquals( "Cannot accept visitor on URL: " + url, exception.getMessage() );
        }
    }

    // private methods --------------------------------------------------------

    private void expectVisitClass( Mock mock, String className, String data )
    {
        mock.expects( atLeastOnce() ).method( "visitClass" ).with( eq( className ), in( data ) );
    }

    private InputStreamConstraint in( String expected )
    {
        return new InputStreamConstraint( expected );
    }
}
