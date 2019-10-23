package se.kth.jdbl.analysis;

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

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Simply collects the set of visited classes.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 * @see #getClasses()
 */
public class CollectorClassFileVisitor
        implements ClassFileVisitor {
    // fields -----------------------------------------------------------------

    private final Set<String> classes;

    // constructors -----------------------------------------------------------

    public CollectorClassFileVisitor() {
        classes = new HashSet<String>();
    }

    // ClassFileVisitor methods -----------------------------------------------

    /*
     * @see org.apache.invoke.shared.dependency.analyzer.ClassFileVisitor#visitClass(java.lang.String,
     *      java.io.InputStream)
     */
    public void visitClass(String className, InputStream in) {
        classes.add(className);
    }

    // public methods ---------------------------------------------------------

    public Set<String> getClasses() {
        return classes;
    }
}
