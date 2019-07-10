package se.kth.jdbl.pom;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import se.kth.jdbl.pom.analysis.DependenciesAnalyzer;

import java.io.File;

/**
 * This Maven mojo creates a fully debloated core file which do not contains any unused dependencies.
 */
@Mojo(name = "jdbl-pom", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class PomDebloaterMojo extends AbstractMojo {

    //--------------------------------/
    //-------- CLASS FIELD/S --------/
    //------------------------------/

    @Parameter(defaultValue = "${mavenProject}", readonly = true)
    private MavenProject mavenProject;

//    @Parameter(defaultValue = "${mavenSession}", readonly = true)
//    private MavenSession mavenSession;
//
//    @Component
//    private ProjectBuilder projectBuilder;

    //--------------------------------/
    //------- PUBLIC METHOD/S -------/
    //------------------------------/

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        getLog().info("STARTING JDBL-POM DEBLOAT");

        String pathToPutDependencies = mavenProject.getBasedir().getAbsolutePath() + File.pathSeparator + "target/dependencies";
        String pathToPutDebloatedPom = mavenProject.getBasedir().getAbsolutePath() + File.pathSeparator + "jdbl-core.xml";

        getLog().info("pathToCopyDependencies: " + pathToPutDependencies);
        getLog().info("pathToPutDebloatedPom: " + pathToPutDebloatedPom);

        DependenciesAnalyzer dependenciesAnalyzer = new DependenciesAnalyzer(mavenProject);

        dependenciesAnalyzer.getUsedUndeclaredDependencies().stream().forEach(s -> System.out.println(s + ","));
        dependenciesAnalyzer.getUsedDeclaredDependencies().stream().forEach(s -> System.out.println(s + ","));
        dependenciesAnalyzer.getUnusedDeclaredDependencies().stream().forEach(s -> System.out.println(s + ","));

//
//        PomDebloaterImp pomDebloaterImp;
//        try {
//
//
//
//
//            pomDebloaterImp = new PomDebloaterImp();
//            pomDebloaterImp.debloatPom(pathToPutDependencies, pathToPutDebloatedPom);
//        } catch (IOException | XmlPullParserException | MavenInvocationException e) {
//            getLog().error(e);
//        }

        getLog().info("JDBL-POM DEBLOAT FINISHED");
    }

}
