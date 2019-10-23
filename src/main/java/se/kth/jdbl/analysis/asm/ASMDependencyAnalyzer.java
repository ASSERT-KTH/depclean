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

import org.codehaus.plexus.component.annotations.Component;
import se.kth.jdbl.analysis.ClassFileVisitorUtils;
import se.kth.jdbl.analysis.DependencyAnalyzer;
import se.kth.jdbl.count.ClassMembersVisitorCounter;

import java.io.IOException;
import java.net.URL;
import java.util.Set;

/**
 * ASMDependencyAnalyzer
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 */
@Component(role = DependencyAnalyzer.class)
public class ASMDependencyAnalyzer
        implements DependencyAnalyzer {
    // DependencyAnalyzer methods ---------------------------------------------

    /*
     * @see org.apache.invoke.shared.dependency.analyzer.DependencyAnalyzer#analyze(java.net.URL)
     */
    public Set<String> analyze(URL url) throws IOException {

        ClassMembersVisitorCounter.resetClassCounters();

        DependencyClassFileVisitor visitor = new DependencyClassFileVisitor();

        ClassFileVisitorUtils.accept(url, visitor);

        return visitor.getDependencies();
    }
}
