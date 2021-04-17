package se.kth.depclean.util;

/**
 * Utility class to determine the operative system being used.
 */
public class OsUtils {

  private static final String OS = System.getProperty("os.name").toLowerCase();

  private OsUtils() {
  }

  public static boolean isUnix() {
    return (OS.contains("nix") || OS.contains("nux") || OS.contains("mac os"));
  }

  public static boolean isWindows() {
    return (OS.contains("win"));
  }
}
