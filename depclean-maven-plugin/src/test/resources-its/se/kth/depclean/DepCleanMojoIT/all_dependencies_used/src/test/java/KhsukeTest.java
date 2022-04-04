import java.io.IOException;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class KhsukeTest implements SomeContract {

  public static void useMain() throws IOException {
    System.out.println("Use annotation");
  }

}
