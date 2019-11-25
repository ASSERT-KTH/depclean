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
     *
     * @param cmd
     * @return
     * @throws IOException
     */
    public static String[] runCommand(String cmd) throws IOException {
        ArrayList list = new ArrayList();
        Process proc = Runtime.getRuntime().exec(cmd);
        InputStream istr = proc.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(istr));
        String str; // Temporary String variable
        while ((str = br.readLine()) != null)
            list.add(str);
        try {
            proc.waitFor();
        } catch (InterruptedException e) {
            System.err.println("Process was interrupted");
        }
        br.close();
        return (String[]) list.toArray(new String[0]);
    }

    /**
     *
     * @param mvnHome
     * @param pomPath
     * @param mvnGoal
     * @return
     * @throws MavenInvocationException
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
