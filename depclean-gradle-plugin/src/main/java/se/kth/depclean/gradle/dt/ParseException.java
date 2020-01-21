package se.kth.depclean.gradle.dt;

public class ParseException extends Exception {

    private static final long serialVersionUID = -5422097493752660982L;

    public ParseException() {
    }

    public ParseException(String message) {
        super(message);
    }

    public ParseException(Throwable cause) {
        super(cause);
    }

    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }

}
