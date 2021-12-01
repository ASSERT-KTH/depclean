// import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import org.apache.commons.io.FileSystemUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.codec.binary.Base64;


public class Main {

  int field = 42;


  public static void useCommonsIO() throws IOException {
    System.out.println(FileSystemUtils.freeSpaceKb("/"));
  }

  public boolean useCommonsLang() {
    String[] array = {"a", "b", "c"};
    return (ArrayUtils.toString(array)).equals("{a,b,c}");
  }

  public static String useCommonsCodec(String str) {
    Base64 base64 = new Base64();
    byte[] bytes = base64.decodeBase64(str);
    String s = new String(bytes);
    String trimmed = s.split("#")[0];
    return trimmed;
  }


}