import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.FileUtils;

/** This module uses commons-io, so its call graph contains the commons-io classes. */
public class UsesCommonsIo {

  public static void main(String[] args) throws IOException {
    FileUtils.writeStringToFile(new File("output.txt"), "Hello DepClean!", StandardCharsets.UTF_8);
  }
}
