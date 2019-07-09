package se.kth.jdbl.tree;

public class ParseException extends Exception {

    /**
     *
     */
    private static final long serialVersionUID = -5422097493752660982L;

    /**
     *
     */
    public ParseException() {
    }

    /**
     * @param message
     */
    public ParseException(String message) {
        super(message);
    }

    /**
     * @param cause
     */
    public ParseException(Throwable cause) {
        super(cause);
    }

    /**
     * @param message
     * @param cause
     */
    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }

}
