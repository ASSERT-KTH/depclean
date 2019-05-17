package se.kth.jdbl;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * Test for the {@link PomManipulator} class.
 */

public class PomManipulatorTest {

//    Model model;
//
//    @Before
//    public void setUp() {
//        model = PomManipulator.readModel("com.google.guava:guava:27.1-jre");
//    }
//
//    @Test
//    public void testReadModel() throws IOException {
//        final Model model = PomManipulator
//                .readModel("com.google.guava:guava:27.1-jre");
//
//        final MavenXpp3Writer writer = new MavenXpp3Writer();
//        final StringWriter sw = new StringWriter();
//        writer.write(sw, model);
//
//        Assert.assertEquals(model.getName(), ("Guava: Google Core Libraries for Java"));
//        Assert.assertNotNull(model.getScm());
//    }
//
//    @Test
//    public void testCreateVarMap() {
//        Map<String, String> properties = PomManipulator.createVarMap(model);
//        System.out.println("PROPERTIES");
//        properties.forEach((key, value) -> {
//            System.out.println("Key : " + key + " Value : " + value);
//        });
//        Assert.assertTrue("contains key", properties.containsKey("animal.sniffer.version"));
//    }
//
//    @Test
//    public void testWritePom() throws IOException {
//        PomManipulator.writePom(Paths.get("src/test/resources/pom.xml"), model);
//
//    }
//
//    @Test
//    public void testRemoveDependencies() {
//        System.out.println("DEPENDENCIES BEFORE");
//        listDependencies();
//        List<Dependency> dependencies = new ArrayList<>();
//        dependencies.add(model.getDependencies().get(1));
//        PomManipulator.removeDependencies(dependencies, model);
//        System.out.println("DEPENDENCIES AFTER");
//        listDependencies();
//
//        try {
//            testWritePom();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//
//    private void listDependencies() {
//        List<String> dependencies = PomManipulator.getDependencies(model);
//        dependencies.stream().forEach(System.out::println);
//    }
//
//    @Test
//    public void testListOwnPom() throws IOException, XmlPullParserException {
//        model = PomManipulator.readModel(new File("pom.xml"));
//        listDependencies();
//
//    }
}