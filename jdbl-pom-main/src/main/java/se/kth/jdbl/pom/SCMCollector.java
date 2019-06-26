package se.kth.jdbl.pom;

import org.apache.maven.model.Model;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.*;

public class SCMCollector {

    public static void main(String[] args) throws IOException, XmlPullParserException {
        selectArtifacts(new File("/home/cesarsv/Documents/artifacts.csv"));
    }

    public static void selectArtifacts(File artifacts) throws XmlPullParserException, IOException {
        BufferedWriter bw = new BufferedWriter(new FileWriter(new File("/home/cesarsv/Documents/subjects.csv")));
        BufferedReader br = new BufferedReader(new FileReader(artifacts));
        String artifact = br.readLine();

        while (artifact != null) {
            artifact = artifact.substring(1, artifact.length() - 1);

            String[] split = artifact.split(":");
            String groupId = split[0];
            String artifactId = split[1];
            String versionId = split[2];

            try {
                PomDownloader.downloadPom("/home/cesarsv/Documents/", groupId, artifactId, versionId);
            } catch (IOException e) {
                System.err.println();
            }

            Model model = PomManipulator.readModel(new File("/home/cesarsv/Documents/pom.xml"));

            if (declaresSCM(model)) {
                try {
                    bw.write(artifact + "," + model.getScm().getUrl() + "\n");
                } catch (IOException e) {
                    continue;
                }
                System.out.println(artifact + "::::::::" + model.getScm().getUrl());
            }
            artifact = br.readLine();

        }
        br.close();
        bw.close();
    }

    private static boolean declaresSCM(Model model) {
        return model.getScm() != null && model.getScm().getUrl() != null;
    }

}
