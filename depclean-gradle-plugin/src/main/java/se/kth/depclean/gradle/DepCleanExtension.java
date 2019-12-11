package se.kth.depclean.gradle;

public class DepCleanExtension {

    private String message = "Default Greeting from Gradle";

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
