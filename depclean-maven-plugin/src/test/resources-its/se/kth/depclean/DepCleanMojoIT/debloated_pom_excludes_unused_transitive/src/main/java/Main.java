import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.apache.commons.compress.compressors.deflate.DeflateCompressorOutputStream;

public class Main {

  public static void main(String[] args) throws IOException {
    try (OutputStream out = new DeflateCompressorOutputStream(new ByteArrayOutputStream())) {
      out.write(42);
    }
  }
}
