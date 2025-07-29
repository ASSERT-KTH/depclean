package se.kth.depclean.util;

import java.util.Locale;

/** Utility class to determine the operative system being used. */
public class OsUtils {

  private static final String OS = System.getProperty("os.name").toLowerCase(Locale.ROOT);

  private OsUtils() {}

  public static boolean isUnix() {
    return OS.contains("nix") || OS.contains("nux") || OS.contains("mac os");
  }

  public static boolean isWindows() {
    return OS.contains("win");
  }
}
