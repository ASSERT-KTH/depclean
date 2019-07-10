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

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/**
 * A small parser to read the constant pool directly, in case it contains references
 * ASM does not support.
 *
 * Adapted from http://stackoverflow.com/a/32278587/23691
 * 
 * Constant pool types:
 * 
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.4">JVM 9 Sepc</a>
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se10/html/jvms-4.html#jvms-4.4">JVM 10 Sepc</a>
 * 
 */
public class ConstantPoolParser
{
    public static final int HEAD = 0xcafebabe;

    // Constant pool types
    public static final byte CONSTANT_UTF8 = 1;

    public static final byte CONSTANT_INTEGER = 3;

    public static final byte CONSTANT_FLOAT = 4;

    public static final byte CONSTANT_LONG = 5;

    public static final byte CONSTANT_DOUBLE = 6;

    public static final byte CONSTANT_CLASS = 7;

    public static final byte CONSTANT_STRING = 8;

    public static final byte CONSTANT_FIELDREF = 9;

    public static final byte CONSTANT_METHODREF = 10;

    public static final byte CONSTANT_INTERFACEMETHODREF = 11;

    public static final byte CONSTANT_NAME_AND_TYPE = 12;

    public static final byte CONSTANT_METHODHANDLE = 15;

    public static final byte CONSTANT_METHOD_TYPE = 16;

    public static final byte CONSTANT_INVOKE_DYNAMIC = 18;
    
    public static final byte CONSTANT_MODULE = 19;

    public static final byte CONSTANT_PACKAGE = 20;

    private static final int OXF0 = 0xf0;

    private static final int OXE0 = 0xe0;

    private static final int OX3F = 0x3F;

    static Set<String> getConstantPoolClassReferences( byte[] b )
    {
        return parseConstantPoolClassReferences( ByteBuffer.wrap( b ) );
    }

    static Set<String> parseConstantPoolClassReferences( ByteBuffer buf )
    {
        if ( buf.order( ByteOrder.BIG_ENDIAN )
                .getInt() != HEAD )
        {
            return Collections.emptySet();
        }
        buf.getChar() ; buf.getChar(); // minor + ver
        Set<Integer> classes = new HashSet<Integer>();
        Map<Integer, String> stringConstants = new HashMap<Integer, String>();
        for ( int ix = 1, num = buf.getChar(); ix < num; ix++ )
        {
            byte tag = buf.get();
            switch ( tag )
            {
                default:
                    throw new RuntimeException( "Unknown constant pool type '" + tag + "'" );
                case CONSTANT_UTF8:
                    stringConstants.put( ix, decodeString( buf ) );
                    continue;
                case CONSTANT_CLASS:
                case CONSTANT_STRING:
                case CONSTANT_METHOD_TYPE:
                    classes.add( (int) buf.getChar() );
                    break;
                case CONSTANT_FIELDREF:
                case CONSTANT_METHODREF:
                case CONSTANT_INTERFACEMETHODREF:
                case CONSTANT_NAME_AND_TYPE:
                    buf.getChar();
                    buf.getChar();
                    break;
                case CONSTANT_INTEGER:
                    buf.getInt();
                    break;
                case CONSTANT_FLOAT:
                    buf.getFloat();
                    break;
                case CONSTANT_DOUBLE:
                    buf.getDouble();
                    ix++;
                    break;
                case CONSTANT_LONG:
                    buf.getLong();
                    ix++;
                    break;
                case CONSTANT_METHODHANDLE:
                    buf.get();
                    buf.getChar();
                    break;
                case CONSTANT_INVOKE_DYNAMIC:
                    buf.getChar();
                    buf.getChar();
                    break;
                case CONSTANT_MODULE:
                    buf.getChar();
                    break;
                case CONSTANT_PACKAGE:
                    buf.getChar();
                    break;  
            }
        }
        Set<String> result = new HashSet<String>();
        for ( Integer aClass : classes )
        {
            result.add( stringConstants.get( aClass ) );
        }
        return result;
    }

    private static String decodeString( ByteBuffer buf )
    {
        int size = buf.getChar();
        // Explicit cast for compatibility with covariant return type on JDK 9's ByteBuffer
        int oldLimit = ( (Buffer) buf ).limit();
        ( (Buffer) buf ).limit( buf.position() + size );
        StringBuilder sb = new StringBuilder( size + ( size >> 1 ) + 16 );
        while ( buf.hasRemaining() )
        {
            byte b = buf.get();
            if ( b > 0 )
            {
                sb.append( (char) b );
            }
            else
            {
                int b2 = buf.get();
                if ( ( b & OXF0 ) != OXE0 )
                {
                    sb.append( (char) ( ( b & 0x1F ) << 6 | b2 & OX3F ) );
                }
                else
                {
                    int b3 = buf.get();
                    sb.append( (char) ( ( b & 0x0F ) << 12 | ( b2 & OX3F ) << 6 | b3 & OX3F ) );
                }
            }
        }
        ( (Buffer) buf ).limit( oldLimit );
        return sb.toString();
    }
}
