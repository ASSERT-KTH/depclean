package se.kth.jdbl.pom.main;
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

import org.apache.commons.lang3.JavaVersion;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalysis;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzer;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzerException;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.test.plugin.BuildTool;
import org.apache.maven.shared.test.plugin.ProjectTool;
import org.apache.maven.shared.test.plugin.RepositoryTool;
import org.apache.maven.shared.test.plugin.TestToolsException;
import org.codehaus.plexus.PlexusTestCase;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

// avoid rat with [mvn -Drat.skip=true package]
public class Main extends PlexusTestCase {
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
            throws Exception {
        super.setUp();

        buildTool = (BuildTool) lookup(BuildTool.ROLE);

        projectTool = (ProjectTool) lookup(ProjectTool.ROLE);

        if (localRepo == null) {
            RepositoryTool repositoryTool = (RepositoryTool) lookup(RepositoryTool.ROLE);
            localRepo = repositoryTool.findLocalRepositoryDirectory();
            System.out.println("Local repository: " + localRepo);
        }

        analyzer = (ProjectDependencyAnalyzer) lookup(ProjectDependencyAnalyzer.ROLE);
    }

    // tests ------------------------------------------------------------------


    public void testGetSystemProperties() {
        Properties properties = System.getProperties();
        properties.forEach((k, v) -> System.out.println(k + ":" + v));
    }

    public void testJarWithTestDependency()
            throws TestToolsException, ProjectDependencyAnalyzerException {

        String projectDirName = "dummy-project";
        compileProject(projectDirName + "/pom.xml");

        MavenProject mavenProject = getProject(projectDirName + "/pom.xml");

        List<String> modules = mavenProject.getModules();
        if (modules.size() > 0) {
            for (String module : modules) {
                compileProject(projectDirName +"/" + module + "/pom.xml");
                System.out.println("----------------------------------------------");
                System.out.println("Analyzing module " + module);
                System.out.println("----------------------------------------------");
                MavenProject moduleProject = getProject(projectDirName +"/" + module + "/pom.xml");
                ProjectDependencyAnalysis actualAnalysis = analyzer.analyze(moduleProject);
                actualAnalysis.ignoreNonCompile();

                System.out.println("Used and declared dependencies");
                actualAnalysis.getUsedDeclaredArtifacts().forEach(x -> System.out.println("\t" + x));

                System.out.println("Used but undeclared dependencies");
                actualAnalysis.getUsedUndeclaredArtifacts().forEach(x -> System.out.println("\t" + x));

                System.out.println("Unused but declared dependencies");
                actualAnalysis.getUnusedDeclaredArtifacts().forEach(x -> System.out.println("\t" + x));
            }
        } else {
            ProjectDependencyAnalysis actualAnalysis = analyzer.analyze(mavenProject);
            actualAnalysis.ignoreNonCompile();
            System.out.println("Used and declared dependencies");
            actualAnalysis.getUsedDeclaredArtifacts().forEach(x -> System.out.println("\t" + x));
            System.out.println("Used but undeclared dependencies");
            actualAnalysis.getUsedUndeclaredArtifacts().forEach(x -> System.out.println("\t" + x));
            System.out.println("Unused but declared dependencies");
            actualAnalysis.getUnusedDeclaredArtifacts().forEach(x -> System.out.println("\t" + x));
        }
    }


    // private methods --------------------------------------------------------

    private void compileProject(String pomPath)
            throws TestToolsException {
        compileProject(pomPath, new Properties());
    }

    private void compileProject(String pomPath, Properties properties) throws TestToolsException {
        File pom = getTestFile("target/test-classes/", pomPath);
        if (SystemUtils.isJavaVersionAtLeast(JavaVersion.JAVA_1_8)
                && !properties.containsKey("maven.compiler.source")) {
            properties.put("maven.compiler.source", "1.8");
            properties.put("maven.compiler.target", "1.8");
        }

        String httpsProtocols = System.getProperty("https.protocols");
        if (httpsProtocols != null) {
            properties.put("https.protocols", httpsProtocols);
        }
        properties.put("maven.home", "/usr/share/maven");

//        List<String> goals = Arrays.asList("clean", "install");
        List<String> goals = Arrays.asList("clean", "compile");
        File log = new File(pom.getParentFile(), "build.log");

        // TODO: don't install test artifacts to local repository
        InvocationRequest request = buildTool.createBasicInvocationRequest(pom, properties, goals, log);
        request.setLocalRepositoryDirectory(localRepo);
        request.setPomFile(pom);
        InvocationResult result = buildTool.executeMaven(request);

        assertNull("Error compiling test project", result.getExecutionException());
        assertEquals("Error compiling test project", 0, result.getExitCode());
    }

    private MavenProject getProject(String pomPath)
            throws TestToolsException {
        File pom = getTestFile("target/test-classes/", pomPath);

        return projectTool.readProjectWithDependencies(pom);
    }

    private Artifact createArtifact(String groupId, String artifactId, String type, String version, String scope) {
        VersionRange versionRange = VersionRange.createFromVersion(version);
        ArtifactHandler handler = new DefaultArtifactHandler();

        return new DefaultArtifact(groupId, artifactId, versionRange, scope, type, null, handler);
    }
}
