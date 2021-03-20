package se.kth.depclean.core.maven.plugin;

import java.io.File;
import org.junit.Assert;
import org.junit.Test;
import se.kth.depclean.util.JarUtils;

public class JarUtilsTest {

    @Test
    public void decompressJarFiles() throws RuntimeException {
        File file = new File("./target/dependency");
        // Verifying presence of jar file.
        if (file.exists() && file.getName().endsWith(".jar") ) {
            try {
                JarUtils.decompressJars(file.getName());
            } catch (RuntimeException e) {
                e.printStackTrace();
                Assert.fail();
            }
        }
    }
}