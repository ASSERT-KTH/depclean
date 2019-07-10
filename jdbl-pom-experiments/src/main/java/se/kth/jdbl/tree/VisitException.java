package se.kth.jdbl.tree;

public class VisitException extends Exception {

    /**
     *
     */
    private static final long serialVersionUID = -8947246553563244540L;

    public VisitException() {
    }

    public VisitException(String message) {
        super(message);
    }

    public VisitException(Throwable cause) {
        super(cause);
    }

    public VisitException(String message, Throwable cause) {
        super(message, cause);
    }

}
