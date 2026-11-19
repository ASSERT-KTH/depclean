import java.io.IOException;
import java.io.StringReader;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

class MainTest {

  @Test
  void readsWithCommonsIo() throws IOException {
    IOUtils.toString(new StringReader("Hello"));
  }
}
