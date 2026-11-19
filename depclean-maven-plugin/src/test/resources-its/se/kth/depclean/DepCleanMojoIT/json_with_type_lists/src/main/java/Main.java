import java.io.IOException;
import java.io.StringReader;
import org.apache.commons.io.IOUtils;

public class Main {

  public static void main(String[] args) throws IOException {
    System.out.println(IOUtils.toString(new StringReader("Hello")));
  }
}
