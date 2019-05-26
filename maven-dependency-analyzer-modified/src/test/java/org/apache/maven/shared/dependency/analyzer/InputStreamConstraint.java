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

import java.io.IOException;
import java.io.InputStream;

import org.codehaus.plexus.util.IOUtil;
import org.jmock.core.Constraint;

/**
 * 
 * 
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 */
public class InputStreamConstraint
    implements Constraint
{
    // constants --------------------------------------------------------------

    private static final String DEFAULT_CHARSET_NAME = "UTF-8";

    // fields -----------------------------------------------------------------

    private final String expected;

    private final String charsetName;

    // constructors -----------------------------------------------------------

    public InputStreamConstraint( String expected )
    {
        this( expected, DEFAULT_CHARSET_NAME );
    }

    public InputStreamConstraint( String expected, String charsetName )
    {
        this.expected = expected;
        this.charsetName = charsetName;
    }

    // Constraint methods -----------------------------------------------------

    /*
     * @see org.jmock.core.Constraint#eval(java.lang.Object)
     */
    public boolean eval( Object object )
    {
        if ( !( object instanceof InputStream ) )
        {
            return false;
        }

        InputStream in = (InputStream) object;

        try
        {
            String actual = IOUtil.toString( in, charsetName );

            return expected.equals( actual );
        }
        catch ( IOException exception )
        {
            return false;
        }
    }

    // SelfDescribing methods -------------------------------------------------

    /*
     * @see org.jmock.core.SelfDescribing#describeTo(java.lang.StringBuffer)
     */
    public StringBuffer describeTo( StringBuffer buffer )
    {
        buffer.append( "in(" );
        buffer.append( "\"" ).append( expected ).append( "\"" );
        buffer.append( "," ).append( charsetName );
        buffer.append( ")" );

        return buffer;
    }
}
