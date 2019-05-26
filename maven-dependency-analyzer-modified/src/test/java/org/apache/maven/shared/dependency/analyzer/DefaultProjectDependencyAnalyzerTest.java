package org.apache.maven.shared.dependency.analyzer;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang3.JavaVersion;
import org.apache.commons.lang3.SystemUtils;

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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.test.plugin.BuildTool;
import org.apache.maven.shared.test.plugin.ProjectTool;
import org.apache.maven.shared.test.plugin.RepositoryTool;
import org.apache.maven.shared.test.plugin.TestToolsException;
import org.codehaus.plexus.PlexusTestCase;

/**
 * Tests <code>DefaultProjectDependencyAnalyzer</code>.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 * @see DefaultProjectDependencyAnalyzer
 */
public class DefaultProjectDependencyAnalyzerTest
    extends PlexusTestCase
{
    // fields -----------------------------------------------------------------

    private BuildTool buildTool;

    private ProjectTool projectTool;

    private ProjectDependencyAnalyzer analyzer;

    private static File localRepo;

    // TestCase methods -------------------------------------------------------

    /*
     * @see org.codehaus.plexus.PlexusTestCase#setUp()
     */
    protected void setUp()
        throws Exception
    {
        super.setUp();

        buildTool = (BuildTool) lookup( BuildTool.ROLE );

        projectTool = (ProjectTool) lookup( ProjectTool.ROLE );

        if ( localRepo == null )
        {
            RepositoryTool repositoryTool = (RepositoryTool) lookup( RepositoryTool.ROLE );
            localRepo = repositoryTool.findLocalRepositoryDirectory();
            System.out.println( "Local repository: " + localRepo );
        }

        analyzer = (ProjectDependencyAnalyzer) lookup( ProjectDependencyAnalyzer.ROLE );
    }

    // tests ------------------------------------------------------------------

//    public void testPom()
//        throws TestToolsException, ProjectDependencyAnalyzerException
//    {
//        compileProject( "pom/pom.xml" );
//
//        MavenProject project = getProject( "pom/pom.xml" );
//
//        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze( project );
//
//        ProjectDependencyAnalysis expectedAnalysis = new ProjectDependencyAnalysis();
//
//        assertEquals( expectedAnalysis, actualAnalysis );
//    }

    public void testJarWithNoDependencies()
        throws TestToolsException, ProjectDependencyAnalyzerException
    {
        compileProject( "jarWithNoDependencies/pom.xml" );

        MavenProject project = getProject( "jarWithNoDependencies/pom.xml" );

        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze( project );

        ProjectDependencyAnalysis expectedAnalysis = new ProjectDependencyAnalysis();

        assertEquals( expectedAnalysis, actualAnalysis );
    }

    public void testJava8methodRefs()
        throws TestToolsException, ProjectDependencyAnalyzerException
    {
        if ( !SystemUtils.isJavaVersionAtLeast( JavaVersion.JAVA_1_8 ) )
        {
            return;
        }

        // Only visible through constant pool analysis (supported for JDK8+)
        compileProject( "java8methodRefs/pom.xml" );

        MavenProject project = getProject( "java8methodRefs/pom.xml" );

        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze( project );

        Artifact project1 = createArtifact( "commons-io", "commons-io", "jar", "2.4", "compile" );
        Artifact project2 = createArtifact( "commons-lang", "commons-lang", "jar", "2.6", "compile" );
        Set<Artifact> usedDeclaredArtifacts = new HashSet<Artifact>( Arrays.asList( project1, project2 ) );

        ProjectDependencyAnalysis expectedAnalysis =
            new ProjectDependencyAnalysis( usedDeclaredArtifacts, new HashSet<Artifact>(), new HashSet<Artifact>() );

        assertEquals( expectedAnalysis, actualAnalysis );
    }

    public void testInlinedStaticReference()
        throws TestToolsException, ProjectDependencyAnalyzerException
    {
        if ( !SystemUtils.isJavaVersionAtLeast( JavaVersion.JAVA_1_8 ) )
        {
            return;
        }

        // Only visible through constant pool analysis (supported for JDK8+)
        compileProject( "inlinedStaticReference/pom.xml" );

        MavenProject project = getProject( "inlinedStaticReference/pom.xml" );

        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze( project );

        Artifact project1 = createArtifact( "dom4j", "dom4j", "jar", "1.6.1", "compile" );
        Set<Artifact> usedDeclaredArtifacts = Collections.singleton( project1 );

        ProjectDependencyAnalysis expectedAnalysis =
            new ProjectDependencyAnalysis( usedDeclaredArtifacts, new HashSet<Artifact>(), new HashSet<Artifact>() );

        assertEquals( expectedAnalysis, actualAnalysis );
    }

    public void testJarWithCompileDependency()
        throws TestToolsException, ProjectDependencyAnalyzerException
    {
        compileProject( "jarWithCompileDependency/pom.xml" );

        MavenProject project2 = getProject( "jarWithCompileDependency/project2/pom.xml" );

        if ( project2.getBuild().getOutputDirectory().contains( "${" ) )
        {
            // if Maven version used as dependency is upgraded to >= 2.2.0
            throw new TestToolsException( "output directory was not interpolated: "
                + project2.getBuild().getOutputDirectory() );
        }

        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze( project2 );

        Artifact project1 = createArtifact( "org.apache.maven.shared.dependency-analyzer.tests",
                                            "jarWithCompileDependency1", "jar", "1.0", "compile" );
        Set<Artifact> usedDeclaredArtifacts = Collections.singleton( project1 );
        ProjectDependencyAnalysis expectedAnalysis = new ProjectDependencyAnalysis( usedDeclaredArtifacts, null, null );

        assertEquals( expectedAnalysis, actualAnalysis );
    }

    public void testForceDeclaredDependenciesUsage()
        throws TestToolsException, ProjectDependencyAnalyzerException
    {
        compileProject( "jarWithTestDependency/pom.xml" );

        MavenProject project2 = getProject( "jarWithTestDependency/project2/pom.xml" );

        ProjectDependencyAnalysis analysis = analyzer.analyze( project2 );

        try
        {
            analysis.forceDeclaredDependenciesUsage( new String[] {
                "org.apache.maven.shared.dependency-analyzer.tests:jarWithTestDependency1" } );
            fail( "failure expected since junit dependency is declared-used" );
        }
        catch ( ProjectDependencyAnalyzerException pdae )
        {
            assertTrue( pdae.getMessage().contains( "Trying to force use of dependencies which are "
                + "declared but already detected as used: "
                + "[org.apache.maven.shared.dependency-analyzer.tests:jarWithTestDependency1]" ) );
        }

        try
        {
            analysis.forceDeclaredDependenciesUsage( new String[] { "undefined:undefined" } );
            fail( "failure expected since undefined dependency is not declared" );
        }
        catch ( ProjectDependencyAnalyzerException pdae )
        {
            assertTrue( pdae.getMessage().contains( "Trying to force use of dependencies which are "
                + "not declared: [undefined:undefined]" ) );
        }
    }

    public void testJarWithTestDependency()
        throws TestToolsException, ProjectDependencyAnalyzerException
    {
        compileProject( "jarWithTestDependency/pom.xml" );

        MavenProject project2 = getProject( "jarWithTestDependency/project2/pom.xml" );

        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze( project2 );

        Artifact project1 = createArtifact( "org.apache.maven.shared.dependency-analyzer.tests",
                                            "jarWithTestDependency1", "jar", "1.0", "test" );
        Artifact junit = createArtifact( "junit", "junit", "jar", "3.8.1", "test" );

        ProjectDependencyAnalysis expectedAnalysis;
        if ( SystemUtils.isJavaVersionAtLeast( JavaVersion.JAVA_1_8 ) )
        {
            Set<Artifact> usedDeclaredArtifacts = new HashSet<Artifact>( Arrays.asList( project1, junit ) );
            expectedAnalysis = new ProjectDependencyAnalysis( usedDeclaredArtifacts, null, null );
        }
        else
        {
            // With JDK 7 and earlier, not all deps are identified correctly
            Set<Artifact> usedDeclaredArtifacts = Collections.singleton( project1 );
            Set<Artifact> unusedDeclaredArtifacts = Collections.singleton( junit );
            expectedAnalysis = new ProjectDependencyAnalysis( usedDeclaredArtifacts, null, unusedDeclaredArtifacts );
        }

        assertEquals( expectedAnalysis, actualAnalysis );
    }

    public void testJarWithXmlTransitiveDependency()
        throws TestToolsException, ProjectDependencyAnalyzerException
    {
        compileProject( "jarWithXmlTransitiveDependency/pom.xml" );

        MavenProject project = getProject( "jarWithXmlTransitiveDependency/pom.xml" );

        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze( project );

        Artifact jdom = createArtifact( "dom4j", "dom4j", "jar", "1.6.1", "compile" );
        Set<Artifact> usedDeclaredArtifacts = Collections.singleton( jdom );

        ProjectDependencyAnalysis expectedAnalysis = new ProjectDependencyAnalysis( usedDeclaredArtifacts, null, null );

        // MSHARED-47: usedUndeclaredArtifacts=[xml-apis:xml-apis:jar:1.0.b2:compile]
        // assertEquals( expectedAnalysis, actualAnalysis );
    }

    public void testMultimoduleProject()
        throws TestToolsException, ProjectDependencyAnalyzerException
    {
        compileProject( "multimoduleProject/pom.xml" );

        // difficult to create multi-module project with Maven 2.x, so here's hacky solution
        // to get a inter-module dependency
        MavenProject project = getProject( "multimoduleProject/module2/pom.xml" );
        @SuppressWarnings( "unchecked" )
        Set<Artifact> dependencyArtifacts = project.getArtifacts();
        for ( Artifact artifact : dependencyArtifacts )
        {
            if ( artifact.getArtifactId().equals( "test-module1" ) )
            {
                File dir = getTestFile( "target/test-classes/", "multimoduleProject/module1/target/classes/" );
                artifact.setFile( dir );
            }
        }

        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze( project );

        Artifact junit = createArtifact( "org.apache.maven.its.dependency", "test-module1", "jar", "1.0", "compile" );
        Set<Artifact> usedDeclaredArtifacts = Collections.singleton( junit );

        ProjectDependencyAnalysis expectedAnalysis = new ProjectDependencyAnalysis( usedDeclaredArtifacts, null, null );

        assertEquals( expectedAnalysis, actualAnalysis );
    }

    public void testTypeUseAnnotationDependency()
            throws TestToolsException, ProjectDependencyAnalyzerException
    {
        // java.lang.annotation.ElementType.TYPE_USE introduced with Java 1.8
        if ( !SystemUtils.isJavaVersionAtLeast( JavaVersion.JAVA_1_8 ) )
        {
            return;
        }

        Properties properties = new Properties();
        properties.put( "maven.compiler.source", "1.8" );
        properties.put( "maven.compiler.target", "1.8" );
        compileProject( "typeUseAnnotationDependency/pom.xml", properties);

        MavenProject usage = getProject( "typeUseAnnotationDependency/usage/pom.xml" );

        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze( usage );

        Artifact annotation = createArtifact( "org.apache.maven.shared.dependency-analyzer.tests",
                                            "typeUseAnnotationDependencyAnnotation", "jar", "1.0", "compile" );
        Set<Artifact> usedDeclaredArtifacts = Collections.singleton( annotation );
        ProjectDependencyAnalysis expectedAnalysis = new ProjectDependencyAnalysis(usedDeclaredArtifacts, null, null);

        assertEquals( expectedAnalysis, actualAnalysis );
    }

    public void testTypeUseAnnotationDependencyOnLocalVariable()
            throws TestToolsException, ProjectDependencyAnalyzerException
    {
        // java.lang.annotation.ElementType.TYPE_USE introduced with Java 1.8
        if ( !SystemUtils.isJavaVersionAtLeast( JavaVersion.JAVA_1_8 ) )
        {
            return;
        }

        Properties properties = new Properties();
        properties.put( "maven.compiler.source", "1.8" );
        properties.put( "maven.compiler.target", "1.8" );
        compileProject( "typeUseAnnotationDependency/pom.xml", properties);

        MavenProject usage = getProject( "typeUseAnnotationDependency/usageLocalVar/pom.xml" );

        ProjectDependencyAnalysis actualAnalysis = analyzer.analyze( usage );

        Artifact annotation = createArtifact( "org.apache.maven.shared.dependency-analyzer.tests",
                                            "typeUseAnnotationDependencyAnnotation", "jar", "1.0", "compile" );
        Set<Artifact> usedDeclaredArtifacts = Collections.singleton( annotation );
        ProjectDependencyAnalysis expectedAnalysis = new ProjectDependencyAnalysis(usedDeclaredArtifacts, null, null);

        assertEquals( expectedAnalysis, actualAnalysis );
    }

    // private methods --------------------------------------------------------

    private void compileProject( String pomPath )
        throws TestToolsException
    {
        compileProject( pomPath, new Properties() );
    }

    private void compileProject(String pomPath, Properties properties) throws TestToolsException {
        File pom = getTestFile( "target/test-classes/", pomPath );
        if ( SystemUtils.isJavaVersionAtLeast( JavaVersion.JAVA_1_8 )
             && !properties.containsKey( "maven.compiler.source" ) )
        {
          properties.put( "maven.compiler.source", "1.8" );
          properties.put( "maven.compiler.target", "1.8" );
        }
        
        String httpsProtocols = System.getProperty( "https.protocols" );
        if ( httpsProtocols != null )
        {
            properties.put( "https.protocols", httpsProtocols );
        }
        properties.put("maven.home", "/usr/share/maven");

        List<String> goals = Arrays.asList( "clean", "install" );
        File log = new File( pom.getParentFile(), "build.log" );

        // TODO: don't install test artifacts to local repository
        InvocationRequest request = buildTool.createBasicInvocationRequest( pom, properties, goals, log );
        request.setLocalRepositoryDirectory( localRepo );
        request.setPomFile(pom);
        InvocationResult result = buildTool.executeMaven( request );

        assertNull( "Error compiling test project", result.getExecutionException() );
        assertEquals( "Error compiling test project", 0, result.getExitCode() );
    }

    private MavenProject getProject( String pomPath )
        throws TestToolsException
    {
        File pom = getTestFile( "target/test-classes/", pomPath );

        return projectTool.readProjectWithDependencies( pom );
    }

    private Artifact createArtifact( String groupId, String artifactId, String type, String version, String scope )
    {
        VersionRange versionRange = VersionRange.createFromVersion( version );
        ArtifactHandler handler = new DefaultArtifactHandler();

        return new DefaultArtifact( groupId, artifactId, versionRange, scope, type, null, handler );
    }
}
