package se.kth.depclean.core.fake.depmanager;

import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Set;
import org.apache.log4j.Logger;

/**
 * A project without any bytecode usage whose XML resources reference a class of the direct
 * dependency (main resources) and a class of the inherited direct dependency (test resources).
 */
public class XmlResourcesDependencyManager extends FakeDependencyManager {

  public XmlResourcesDependencyManager(Logger log) {
    super(log);
  }

  @Override
  public Set<Path> getResourcesDirectories() {
    return ImmutableSet.of(END_2_END_PATH.resolve("xml-resources").resolve("main"));
  }

  @Override
  public Set<Path> getTestResourcesDirectories() {
    return ImmutableSet.of(END_2_END_PATH.resolve("xml-resources").resolve("test"));
  }
}
