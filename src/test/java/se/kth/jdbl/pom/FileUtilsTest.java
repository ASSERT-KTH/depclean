package se.kth.jdbl.pom;

import org.junit.Assert;
import org.junit.Test;
import se.kth.jdbl.pom.util.FileUtils;

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