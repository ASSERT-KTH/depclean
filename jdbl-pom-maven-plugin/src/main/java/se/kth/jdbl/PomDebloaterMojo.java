package se.kth.jdbl;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.IOException;

/**
 * This Maven mojo creates a debloated pom file that do not have any unused dependencies.
 */
@Mojo(name = "pom-debloat", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class PomDebloaterMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        System.out.println("POM DEBLOAT STARTED");

        String pathToCopyDependencies = project.getBasedir().getAbsolutePath() + "/" + "target/dependency";
        String pathToPutDebloatedPom = project.getBasedir().getAbsolutePath() + "/" + "jdbl-pom.xml";

        System.out.println("pathToCopyDependencies: " + pathToCopyDependencies);
        System.out.println("pathToPutDebloatedPom: " + pathToPutDebloatedPom);

        try {
            PomDebloater.debloatPom(pathToCopyDependencies, pathToPutDebloatedPom);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (XmlPullParserException | MavenInvocationException e) {
            e.printStackTrace();
        }

        System.out.println("POM DEBLOAT FINISHED");
    }
}
