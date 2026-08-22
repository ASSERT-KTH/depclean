import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.FileUtils;

/**
 * Uses commons-io, which is declared in the pom with property placeholders in its coordinates.
 */
public class Main {

  public static void main(String[] args) throws IOException {
    FileUtils.writeStringToFile(new File("output.txt"), "Hello DepClean!", StandardCharsets.UTF_8);
  }
}
