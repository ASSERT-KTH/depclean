/*
 * Copyright (c) 2020, CASTOR Software Research Centre (www.castor.kth.se)
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package se.kth.depclean.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class to execute Maven tasks from the command line.
 */
@Slf4j
public final class MavenInvoker {

  private MavenInvoker() {
  }

  /**
   * Creates a native process to execute a custom command. This method is used to invoke maven plugins directly.
   *
   * @param cmd The command to be executed.
   * @return The console output.
   * @throws IOException          In case of IO issues.
   * @throws InterruptedException In case of IO issues.
   */
  public static String[] runCommand(String cmd) throws IOException, InterruptedException {
    Process process;
    ArrayList<String> list;
    if (OsUtils.isUnix()) {
      list = new ArrayList<>();
      process = Runtime.getRuntime().exec(cmd);
      InputStream inputStream = process.getInputStream();
      BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
      return outputToConsole(process, list, br);
    } else if (OsUtils.isWindows()) {
      list = new ArrayList<>();
      process = Runtime.getRuntime().exec("cmd /C " + cmd);
      BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
      return outputToConsole(process, list, br);
    }
    return new String[0]; // return an empty array otherwise
  }

  /**
   * Print the output of the command to the standard output.
   */
  private static String[] outputToConsole(Process process, ArrayList<String> list, BufferedReader br)
      throws IOException, InterruptedException {
    String s;
    while ((s = br.readLine()) != null) {
      list.add(s);
    }
    process.waitFor();
    br.close();
    return list.toArray(new String[0]);
  }
}
