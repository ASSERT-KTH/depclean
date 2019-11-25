package se.kth.depclean.core.maven.plugin;

import org.junit.Assert;
import org.junit.Test;
import se.kth.depclean.util.FileUtils;

import java.io.File;
import java.io.IOException;

public class FileUtilsTest {

    @Test
    public void deleteDirectory() throws IOException {
        File file = new File("./target/dependency");
        if(file.exists()) {
            FileUtils.deleteDirectory(new File("./target/dependency"));
        }
        Assert.assertFalse(file.exists());
    }
}