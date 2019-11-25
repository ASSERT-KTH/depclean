package se.kth.depclean;

import org.gradle.api.Project;

public class GradlePluginExampleExtension {

    private String sentenceToDisplay;

    public void create(Project project) {

        // The code that will implement the logic should be implemented here.
        System.out.println("Sentence: " + sentenceToDisplay);
    }

    public String getSentenceToDisplay() {
        return sentenceToDisplay;
    }

    public void setSentenceToDisplay(String sentenceToDisplay) {
        this.sentenceToDisplay = sentenceToDisplay;
    }
}
