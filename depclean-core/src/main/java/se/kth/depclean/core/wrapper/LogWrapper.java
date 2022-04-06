package se.kth.depclean.core.wrapper;

/**
 * Wraps the dependency manager's logger.
 */
public interface LogWrapper {

  /**
   * Logs at info level.
   *
   * @param message the message to log
   */
  void info(String message);

  /**
   * Logs at error level.
   *
   * @param message the message to log
   */
  void error(String message);

  /**
   * Logs at debug level.
   *
   * @param message the message to log
   */
  void debug(String message);
}
