package se;

import java.io.IOException;
import org.kohsuke.MetaInfServices;
import se.kth.Main;

@MetaInfServices
public class KhsukeTest implements SomeContract {

  public static void useMain() throws IOException {
    Main.useCommonsIO();
  }

}
