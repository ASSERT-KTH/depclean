package se.kth.depclean.core.analysis;

/** Indicates the analysis should fail. */
public class AnalysisFailureException extends Exception {

  /**
   * Create the failure.
   *
   * @param message the message explaining why the analysis failed
   */
  public AnalysisFailureException(String message) {
    super(message);
  }

  /**
   * Create the failure with a root cause.
   *
   * @param message the message explaining why the analysis failed
   * @param cause the underlying exception
   */
  public AnalysisFailureException(String message, Throwable cause) {
    super(message, cause);
  }
}
