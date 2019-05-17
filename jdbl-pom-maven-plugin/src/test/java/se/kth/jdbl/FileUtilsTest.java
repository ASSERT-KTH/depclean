package se.kth.jdbl;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

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