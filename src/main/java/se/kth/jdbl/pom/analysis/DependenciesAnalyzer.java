package se.kth.jdbl.pom.analysis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.analyzer.DefaultProjectDependencyAnalyzer;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalysis;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzer;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzerException;

import java.util.Set;

public class DependenciesAnalyzer {

    //--------------------------------/
    //-------- CLASS FIELD/S --------/
    //------------------------------/

    private ProjectDependencyAnalysis analyzer;

    private static final Logger LOGGER = LogManager.getLogger(DependenciesAnalyzer.class.getName());

    //--------------------------------/
    //-------- CONSTRUCTOR/S --------/
    //------------------------------/

    public DependenciesAnalyzer(MavenProject mavenProject) {
        ProjectDependencyAnalyzer analyzer = new DefaultProjectDependencyAnalyzer();

//        Model model = null;
//        FileReader reader = null;
//        MavenXpp3Reader mavenReader = new MavenXpp3Reader();
//        try {
//            reader = new FileReader(pomFile);
//            model = mavenReader.read(reader);
//            model.setPomFile(pomFile);
//        } catch (Exception ex) {
//            LOGGER.error("Unable to build maven project");
//        }
//        MavenProject mavenProject = new MavenProject(model);

        try {
            this.analyzer = analyzer.analyze(mavenProject);
            this.analyzer.ignoreNonCompile();
        } catch (ProjectDependencyAnalyzerException e) {
            LOGGER.error("Unable to analyze maven project");
        }
    }

    //--------------------------------/
    //------- PUBLIC METHOD/S -------/
    //------------------------------/

    public Set<Artifact> getUsedDeclaredDependencies() {
        return analyzer.getUsedDeclaredArtifacts();
    }

    public Set<Artifact> getUsedUndeclaredDependencies() {
        return analyzer.getUsedDeclaredArtifacts();
    }

    public Set<Artifact> getUnusedDeclaredDependencies() {
        return analyzer.getUnusedDeclaredArtifacts();
    }
}
