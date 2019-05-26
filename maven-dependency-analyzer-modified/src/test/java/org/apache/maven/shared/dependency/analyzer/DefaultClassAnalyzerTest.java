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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipException;

import org.codehaus.plexus.util.IOUtil;

/**
 * Tests <code>DefaultClassAnalyzer</code>.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 * @see DefaultClassAnalyzer
 */
public class DefaultClassAnalyzerTest
    extends AbstractFileTest
{
    // tests ------------------------------------------------------------------

    public void testAnalyzeWithJar()
        throws IOException
    {
        File file = createJar();
        JarOutputStream out = new JarOutputStream( new FileOutputStream( file ) );
        writeEntry( out, "a/b/c.class", "class a.b.c" );
        writeEntry( out, "x/y/z.class", "class x.y.z" );
        out.close();

        Set<String> expectedClasses = new HashSet<String>();
        expectedClasses.add( "a.b.c" );
        expectedClasses.add( "x.y.z" );

        DefaultClassAnalyzer analyzer = new DefaultClassAnalyzer();
        Set<String> actualClasses = analyzer.analyze( file.toURI().toURL() );

        assertEquals( expectedClasses, actualClasses );
    }

    public void testAnalyzeBadJar()
        throws IOException
    {
        //to reproduce MDEP-143
        File file = createJar();
        JarOutputStream out = new JarOutputStream( new FileOutputStream( file ) );
        writeEntry( out, "a/b/c.class", "class a.b.c" );
        writeEntry( out, "x/y/z.class", "class x.y.z" );
        out.close();

        //corrupt the jar file by alter its contents
        FileInputStream fis = new FileInputStream( file );
        ByteArrayOutputStream baos = new ByteArrayOutputStream( 100 );
        IOUtil.copy( fis, baos, 100 );
        fis.close();
        byte [] ba = baos.toByteArray();
        ba[50] = 1;
        FileOutputStream fos = new FileOutputStream( file );
        IOUtil.copy( ba, fos );
        fos.close();

        DefaultClassAnalyzer analyzer = new DefaultClassAnalyzer();

        try
        {
            analyzer.analyze( file.toURI().toURL() );
            fail( "Exception expected" );
        }
        catch ( ZipException e )
        {
            assertTrue( e.getMessage().startsWith( "Cannot process Jar entry on URL:" ) );
        }

    }
}
