package se.kth.jdbl.pom.invoke;

import org.apache.maven.shared.invoker.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;

public class MavenInvoker {

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