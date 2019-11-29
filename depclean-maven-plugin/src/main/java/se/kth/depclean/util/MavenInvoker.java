package se.kth.depclean.util;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;

public final class MavenInvoker {

    //--------------------------------/
    //-------- CONSTRUCTOR/S --------/
    //------------------------------/

    private MavenInvoker() {
    }

    //--------------------------------/
    //------- PUBLIC METHOD/S -------/
    //------------------------------/

    /**
     * Creates a native process to execute a custom command. This method is used to invoke maven plugins directly.
     *
     * @param cmd The command to be executed.
     * @return The console output.
     * @throws IOException In case of IO issues.
     */
    public static String[] runCommand(String cmd) throws IOException {
        ArrayList list = new ArrayList();
        Process process = Runtime.getRuntime().exec(cmd);
        InputStream inputStream = process.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        String s; // Temporary String variable
        while ((s = br.readLine()) != null)
            list.add(s);
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            System.err.println("Process was interrupted");
        }
        br.close();
        return (String[]) list.toArray(new String[0]);
    }

    /**
     * This method invokes Maven to execute a given goal programmatically instead of running a command directly as
     * in {@link #runCommand(String)}.
     *
     * @param mvnHome Location of maven installation.
     * @param pomPath Path to the pom of the project.
     * @param mvnGoal The maven goal to execute.
     * @return The exit code from the Maven invocation.
     * @throws MavenInvocationException In case of any issue invoking maven.
     */
    public static int invokeMaven(String mvnHome, String pomPath, String mvnGoal) throws MavenInvocationException {
        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(new File(pomPath));
        request.setGoals(Collections.singletonList(mvnGoal));
        Invoker invoker = new DefaultInvoker();
        invoker.setMavenHome(new File(mvnHome));
        InvocationResult result = invoker.execute(request);
        return result.getExitCode();
    }
}
