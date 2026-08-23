package smoke;

import java.io.File;
import org.apache.commons.io.FileUtils;

/** Uses commons-io so DepClean reports it as used; commons-lang3 stays unused. */
public class Main {

  public static void main(String[] args) {
    System.out.println(FileUtils.byteCountToDisplaySize(new File(".").length()));
  }
}
