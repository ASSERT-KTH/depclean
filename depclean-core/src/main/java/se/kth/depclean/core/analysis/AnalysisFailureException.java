package se.kth.depclean.core.analysis;

/**
 * Indicates the analysis should fail.
 */
public class AnalysisFailureException extends Exception {

  /**
   * Create the failure.
   *
   * @param message the message to explain with the analysis failed
   */
  public AnalysisFailureException(String message) {
    super(message);
  }
}
