package se.kth.jdbl;

import org.apache.maven.shared.invoker.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;

public class MavenInvoker {

    public static String[] runCommand(String cmd) throws IOException {
        //s The actual procedure for process execution:
        //runCommand(String cmd);
        // Create a list for storing output.
        ArrayList list = new ArrayList();
        // Execute a command and get its process handle
        Process proc = Runtime.getRuntime().exec(cmd);
        // Get the handle for the processes InputStream
        InputStream istr = proc.getInputStream();
        // Create a BufferedReader and specify it reads
        // from an input stream.
        BufferedReader br = new BufferedReader(new InputStreamReader(istr));
        String str; // Temporary String variable
        // Read to Temp Variable, Check for null then
        // add to (ArrayList)list
        while ((str = br.readLine()) != null)
            list.add(str);
        // Wait for process to terminate and catch any Exceptions.
        try {
            proc.waitFor();
        } catch (InterruptedException e) {
            System.err.println("Process was interrupted");
        }
        // Note: proc.exitValue() returns the exit value.
        // (Use if required)
        br.close(); // Done.
        // Convert the list to a string and return
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