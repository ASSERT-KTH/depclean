// import com.fasterxml.jackson.databind.ObjectMapper;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream;

public class Main {

  public static void main(String[] args) throws IOException {
    OutputStream out = new ByteArrayOutputStream();
    OutputStream tmp = new LZMACompressorOutputStream(out);
    System.out.println(tmp);
  }

}