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
import java.io.OutputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.jmock.MockObjectTestCase;

/**
 * 
 * 
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 */
public abstract class AbstractFileTest extends MockObjectTestCase
{
    // protected methods ------------------------------------------------------

    protected File createJar() throws IOException
    {
        File file = File.createTempFile( "test", ".jar" );
        file.deleteOnExit();

        return file;
    }

    protected File createDir() throws IOException
    {
        File file = File.createTempFile( "test", null );
        file.delete();

        if ( !file.mkdir() )
            throw new IOException( "Cannot create temporary directory: " + file );

        return file;
    }

    protected File createFile( File parent, String child, String data ) throws IOException
    {
        File file = new File( parent, child );

        OutputStream out = new FileOutputStream( file );
        IOUtil.copy( data, out );
        out.close();

        return file;
    }

    protected File mkdirs( File parent, String child ) throws IOException
    {
        File dir = new File( parent, child );

        FileUtils.forceMkdir( dir );

        return dir;
    }

    protected void writeEntry( JarOutputStream out, String path, String data ) throws IOException
    {
        out.putNextEntry( new ZipEntry( path ) );

        byte[] bytes = data.getBytes( "UTF-8" );

        out.write( bytes, 0, bytes.length );
    }
}
