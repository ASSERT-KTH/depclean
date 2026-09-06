import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.FileUtils;

/** Uses commons-io only; commons-compress stays unused. */
public class Greeter {

  public static void main(String[] args) throws IOException {
    FileUtils.writeStringToFile(new File("greeting.txt"), "hello", StandardCharsets.UTF_8);
  }
}
