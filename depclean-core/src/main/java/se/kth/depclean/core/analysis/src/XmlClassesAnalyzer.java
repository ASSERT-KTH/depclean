package se.kth.depclean.core.analysis.src;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Collects fully qualified class names referenced in XML resource files, such as Spring XML
 * configurations ({@code <bean class="..."/>}), web deployment descriptors ({@code <filter-class>},
 * {@code <servlet-class>}, {@code <listener-class>}), persistence configurations, logging
 * configurations, etc.
 *
 * <p>Rather than understanding each XML dialect, this analyzer harvests every attribute value and
 * element text node that looks like a fully qualified class name. False positives are harmless: the
 * analysis only considers harvested names that actually resolve to a class of a known dependency.
 */
public final class XmlClassesAnalyzer {

  private static final Logger log = LoggerFactory.getLogger(XmlClassesAnalyzer.class);

  /** A directory with XML resource files. */
  private final Path directoryPath;

  public XmlClassesAnalyzer(Path directoryPath) {
    this.directoryPath = directoryPath;
  }

  /**
   * A dotted identifier with at least two segments (e.g. {@code org.example.Foo}). Possessive
   * quantifiers keep the matching linear; class-name filtering happens in {@link
   * #trimToClassName(String)}.
   */
  private static final Pattern DOTTED_IDENTIFIER_PATTERN =
      Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*+(?:\\.[A-Za-z_$][A-Za-z0-9_$]*+)++");

  /** Guard against pathological inputs: XML files larger than this are skipped. */
  private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

  /**
   * Collects the class names referenced in all the XML files in the directory, recursively.
   *
   * <p>For each harvested name, plausible alternative spellings are also returned: enclosing
   * (outer) classes and the binary name of inner classes (e.g. {@code a.b.Outer.Inner} also yields
   * {@code a.b.Outer} and {@code a.b.Outer$Inner}). Malformed or unreadable XML files are skipped.
   *
   * @return the set of class names referenced in XML files
   */
  public Set<String> collectReferencedClassesFromXml() {
    if (!Files.isReadable(directoryPath) || !Files.isDirectory(directoryPath)) {
      return Collections.emptySet();
    }
    Set<String> classes = new HashSet<>();
    for (File file :
        FileUtils.listFiles(
            directoryPath.toFile(),
            new SuffixFileFilter(".xml", IOCase.INSENSITIVE),
            TrueFileFilter.INSTANCE)) {
      classes.addAll(collectFromFile(file));
    }
    return classes;
  }

  private Set<String> collectFromFile(File file) {
    if (file.length() > MAX_FILE_SIZE_BYTES) {
      log.info("Skipping XML file larger than {} bytes: {}", MAX_FILE_SIZE_BYTES, file);
      return Collections.emptySet();
    }
    ClassNameCollector collector = new ClassNameCollector();
    try {
      newSecureSaxParser().parse(file, collector);
    } catch (Exception e) { // NOPMD - any parsing issue must only skip this file
      log.info("Cannot analyze XML file: {}", file.getAbsolutePath());
    }
    // Keep whatever was harvested before a potential parse failure
    return collector.getClassNames();
  }

  /**
   * Creates a SAX parser hardened against XXE attacks: external entities and external DTDs are
   * never resolved. DOCTYPE declarations themselves remain allowed since legacy descriptors (e.g.
   * Servlet 2.3 web.xml or old Spring beans files) legitimately declare them.
   */
  private static SAXParser newSecureSaxParser() throws ParserConfigurationException, SAXException {
    SAXParserFactory factory = SAXParserFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setXIncludeAware(false);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    return factory.newSAXParser();
  }

  /**
   * Extracts all class-name-looking tokens from a string, including outer-class and binary
   * inner-class spellings.
   *
   * @param value the string to scan
   * @return the harvested class names
   */
  static Set<String> extractClassNames(@Nullable String value) {
    if (value == null || value.indexOf('.') < 0) {
      return Collections.emptySet();
    }
    Set<String> result = new HashSet<>();
    Matcher matcher = DOTTED_IDENTIFIER_PATTERN.matcher(value);
    while (matcher.find()) {
      String className = trimToClassName(matcher.group());
      if (className != null) {
        addSpellingVariants(className, result);
      }
    }
    return result;
  }

  /**
   * Truncates a dotted identifier after its last segment starting with an uppercase letter (the
   * Java class naming convention), dropping trailing member or package segments. Returns {@code
   * null} when no segment except the first is class-like (e.g. package names, URLs, versions).
   */
  @Nullable
  private static String trimToClassName(String token) {
    int end = -1;
    int segmentStart = 0;
    for (int i = 0; i <= token.length(); i++) {
      if (i == token.length() || token.charAt(i) == '.') {
        if (segmentStart > 0 && Character.isUpperCase(token.charAt(segmentStart))) {
          end = i;
        }
        segmentStart = i + 1;
      }
    }
    return end < 0 ? null : token.substring(0, end);
  }

  /**
   * Adds the token and its alternative spellings. XML files reference nested classes either with
   * dots ({@code a.b.Outer.Inner}) or with the binary name ({@code a.b.Outer$Inner}), while
   * dependency class lists use binary names; both spellings and all enclosing classes are added.
   */
  private static void addSpellingVariants(String token, Set<String> result) {
    result.add(token);
    String current = token;
    while (true) {
      int lastDot = current.lastIndexOf('.');
      if (lastDot <= 0) {
        return;
      }
      String head = current.substring(0, lastDot);
      int headLastSegmentStart = head.lastIndexOf('.') + 1;
      if (!Character.isUpperCase(head.charAt(headLastSegmentStart))) {
        return;
      }
      result.add(head);
      current = head + '$' + current.substring(lastDot + 1);
      result.add(current);
    }
  }

  /** Harvests class names from every attribute value and element text node. */
  private static final class ClassNameCollector extends DefaultHandler {

    private final Set<String> classNames = new HashSet<>();
    private final Deque<StringBuilder> textStack = new ArrayDeque<>();

    Set<String> getClassNames() {
      return classNames;
    }

    @Override
    public InputSource resolveEntity(@Nullable String publicId, @Nullable String systemId) {
      // Never resolve external entities (defense in depth, on top of the parser features)
      return new InputSource(new StringReader(""));
    }

    @Override
    public void startElement(
        @Nullable String uri,
        @Nullable String localName,
        @Nullable String qualifiedName,
        @Nullable Attributes attributes) {
      if (attributes != null) {
        for (int i = 0; i < attributes.getLength(); i++) {
          classNames.addAll(extractClassNames(attributes.getValue(i)));
        }
      }
      appendBoundaryToParent();
      textStack.push(new StringBuilder());
    }

    @Override
    public void characters(char[] characters, int start, int length) {
      StringBuilder current = textStack.peek();
      if (current != null) {
        current.append(characters, start, length);
      }
    }

    @Override
    public void endElement(
        @Nullable String uri, @Nullable String localName, @Nullable String qualifiedName) {
      StringBuilder current = textStack.poll();
      if (current != null) {
        classNames.addAll(extractClassNames(current.toString()));
      }
      appendBoundaryToParent();
    }

    /** Prevents text on both sides of a nested element from merging into one token. */
    private void appendBoundaryToParent() {
      StringBuilder parent = textStack.peek();
      if (parent != null) {
        parent.append(' ');
      }
    }
  }
}
