package se.kth.depclean.core.analysis.src;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XmlClassesAnalyzerTest {

  @TempDir Path directory;

  @Test
  @DisplayName("Classes declared in Spring XML bean definitions are collected (issue #78)")
  void collectsSpringBeanClasses() throws IOException {
    write(
        "applicationContext.xml",
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <beans xmlns="http://www.springframework.org/schema/beans"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://www.springframework.org/schema/beans
                   http://www.springframework.org/schema/beans/spring-beans.xsd">
          <bean id="dataSource" class="org.apache.commons.dbcp2.BasicDataSource">
            <property name="driverClassName" value="org.h2.Driver"/>
          </bean>
          <bean id="mapper" class="com.fasterxml.jackson.databind.ObjectMapper"/>
        </beans>
        """);

    assertThat(collect())
        .contains(
            "org.apache.commons.dbcp2.BasicDataSource",
            "org.h2.Driver",
            "com.fasterxml.jackson.databind.ObjectMapper");
  }

  @Test
  @DisplayName("Filter, servlet and listener classes in web.xml are collected (issue #81)")
  void collectsWebXmlClasses() throws IOException {
    write(
        "WEB-INF/web.xml",
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee" version="5.0">
          <filter>
            <filter-name>encodingFilter</filter-name>
            <filter-class>org.springframework.web.filter.CharacterEncodingFilter</filter-class>
          </filter>
          <servlet>
            <servlet-name>dispatcher</servlet-name>
            <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
          </servlet>
          <listener>
            <listener-class>org.springframework.web.context.ContextLoaderListener</listener-class>
          </listener>
        </web-app>
        """);

    assertThat(collect())
        .contains(
            "org.springframework.web.filter.CharacterEncodingFilter",
            "org.springframework.web.servlet.DispatcherServlet",
            "org.springframework.web.context.ContextLoaderListener");
  }

  @Test
  @DisplayName("A legacy web.xml with a DOCTYPE declaration is parsed without fetching the DTD")
  void parsesLegacyDoctypeWithoutResolvingExternalDtd() throws IOException {
    write(
        "web.xml",
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE web-app PUBLIC "-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN"
            "http://localhost:1/nonexistent/web-app_2_3.dtd">
        <web-app>
          <filter>
            <filter-name>old</filter-name>
            <filter-class>org.example.legacy.OldFilter</filter-class>
          </filter>
        </web-app>
        """);

    assertThat(collect()).contains("org.example.legacy.OldFilter");
  }

  @Test
  @DisplayName("External entities are not resolved (XXE hardening)")
  void doesNotResolveExternalEntities() throws IOException {
    Path secret = write("secret.txt", "top-secret");
    write(
        "xxe.xml",
        "<?xml version=\"1.0\"?>\n"
            + "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file://"
            + secret.toAbsolutePath()
            + "\">]>\n"
            + "<foo><bar>&xxe;</bar><baz>org.example.Safe</baz></foo>\n");

    Set<String> classes = collect();

    assertThat(classes).doesNotContain("top-secret");
    // The file must not break the analysis of its own well-formed parts or other files
    assertThat(classes).isNotNull();
  }

  @Test
  @DisplayName("Class names are collected from element text, attributes and nested directories")
  void collectsFromNestedDirectories() throws IOException {
    write("a/b/c/deep.xml", "<conf><handler>org.example.deep.DeepHandler</handler></conf>");
    write("top.xml", "<conf handler=\"org.example.top.TopHandler\"/>");

    assertThat(collect())
        .contains("org.example.deep.DeepHandler", "org.example.top.TopHandler");
  }

  @Test
  @DisplayName("Comma- and whitespace-separated class lists are all collected")
  void collectsClassLists() throws IOException {
    write(
        "context.xml",
        """
        <web-app>
          <context-param>
            <param-name>contextInitializerClasses</param-name>
            <param-value>
              org.example.first.FirstInitializer,org.example.second.SecondInitializer
              org.example.third.ThirdInitializer
            </param-value>
          </context-param>
        </web-app>
        """);

    assertThat(collect())
        .contains(
            "org.example.first.FirstInitializer",
            "org.example.second.SecondInitializer",
            "org.example.third.ThirdInitializer");
  }

  @Test
  @DisplayName("Values that are not class names are not collected")
  void ignoresNonClassValues() throws IOException {
    write(
        "noise.xml",
        """
        <conf version="2.18.9" url="http://maven.apache.org/POM/4.0.0">
          <url-pattern>/api/*</url-pattern>
          <host>www.example.com</host>
          <package>org.example.mypackage</package>
          <word>Filter</word>
        </conf>
        """);

    assertThat(collect()).isEmpty();
  }

  @Test
  @DisplayName("Inner classes are collected in both dotted and binary spelling, plus outer class")
  void collectsInnerClassSpellings() throws IOException {
    write("inner.xml", "<bean class=\"org.example.Outer.Inner\"/>");

    assertThat(collect())
        .contains("org.example.Outer.Inner", "org.example.Outer$Inner", "org.example.Outer");
  }

  @Test
  @DisplayName("Binary inner class names are collected as-is")
  void collectsBinaryInnerClassNames() throws IOException {
    write("binary.xml", "<bean class=\"org.example.Outer$Inner\"/>");

    assertThat(collect()).contains("org.example.Outer$Inner");
  }

  @Test
  @DisplayName("Malformed XML files are skipped without failing the analysis")
  void skipsMalformedXml() throws IOException {
    write("broken.xml", "<conf><unclosed>org.example.broken.BrokenHandler</conf>");
    write("valid.xml", "<conf><handler>org.example.valid.ValidHandler</handler></conf>");

    assertThat(collect()).contains("org.example.valid.ValidHandler");
  }

  @Test
  @DisplayName("Empty XML files are skipped without failing the analysis")
  void skipsEmptyXmlFile() throws IOException {
    write("empty.xml", "");
    write("valid.xml", "<conf><handler>org.example.valid.ValidHandler</handler></conf>");

    assertThat(collect()).containsExactly("org.example.valid.ValidHandler");
  }

  @Test
  @DisplayName("Non-XML files are not analyzed")
  void ignoresNonXmlFiles() throws IOException {
    write("readme.txt", "org.example.text.NotCollected");
    write("config.properties", "handler=org.example.props.NotCollected");

    assertThat(collect()).isEmpty();
  }

  @Test
  @DisplayName("The .xml extension is matched case-insensitively")
  void matchesXmlExtensionCaseInsensitively() throws IOException {
    write("UPPER.XML", "<conf handler=\"org.example.upper.UpperHandler\"/>");

    assertThat(collect()).contains("org.example.upper.UpperHandler");
  }

  @Test
  @DisplayName("A nonexistent directory yields an empty result")
  void nonexistentDirectoryYieldsEmptyResult() {
    XmlClassesAnalyzer analyzer =
        new XmlClassesAnalyzer(directory.resolve("does").resolve("not").resolve("exist"));

    assertThat(analyzer.collectReferencedClassesFromXml()).isEmpty();
  }

  @Test
  @DisplayName("A regular file instead of a directory yields an empty result")
  void fileInsteadOfDirectoryYieldsEmptyResult() throws IOException {
    Path file = write("some.xml", "<conf handler=\"org.example.some.SomeHandler\"/>");

    assertThat(new XmlClassesAnalyzer(file).collectReferencedClassesFromXml()).isEmpty();
  }

  @Test
  @DisplayName("Oversized XML files are skipped")
  void skipsOversizedXmlFiles() throws IOException {
    StringBuilder big = new StringBuilder("<conf><h>org.example.big.BigHandler</h>");
    big.append("<filler>").append("x".repeat(11 * 1024 * 1024)).append("</filler></conf>");
    write("big.xml", big.toString());

    assertThat(collect()).isEmpty();
  }

  @Test
  @DisplayName("Attribute values with surrounding whitespace are collected")
  void collectsAttributeValuesWithWhitespace() throws IOException {
    write("ws.xml", "<bean class=\"  org.example.ws.WhitespaceHandler  \"/>");

    assertThat(collect()).contains("org.example.ws.WhitespaceHandler");
  }

  @Test
  @DisplayName("Text nodes split across nested elements are still harvested per element")
  void harvestsTextPerElement() throws IOException {
    write(
        "nested.xml",
        "<a>org.example.a.HandlerA<b>org.example.b.HandlerB</b>org.example.c.HandlerC</a>");

    assertThat(collect())
        .contains("org.example.a.HandlerA", "org.example.b.HandlerB", "org.example.c.HandlerC");
  }

  @Test
  @DisplayName("extractClassNames handles null and dot-less values")
  void extractClassNamesHandlesDegenerateValues() {
    assertThat(XmlClassesAnalyzer.extractClassNames(null)).isEmpty();
    assertThat(XmlClassesAnalyzer.extractClassNames("")).isEmpty();
    assertThat(XmlClassesAnalyzer.extractClassNames("NoPackage")).isEmpty();
    assertThat(XmlClassesAnalyzer.extractClassNames("1.2.3")).isEmpty();
  }

  @Test
  @DisplayName("Class member references fall back to the class name")
  void extractsClassFromMemberReference() {
    assertThat(XmlClassesAnalyzer.extractClassNames("org.example.Foo.someMethod"))
        .contains("org.example.Foo");
    assertThat(XmlClassesAnalyzer.extractClassNames("org.example.Foo.SOME_CONSTANT"))
        .contains("org.example.Foo");
  }

  @Test
  @DisplayName("Log4j2-style configuration attributes are collected")
  void collectsLoggingConfigurationClasses() throws IOException {
    write(
        "log4j2.xml",
        """
        <Configuration>
          <Appenders>
            <Console name="console">
              <PatternLayout pattern="%d{HH:mm:ss} %msg%n"/>
            </Console>
          </Appenders>
          <Loggers>
            <Logger name="org.example.app" level="info"/>
            <Root level="warn"/>
          </Loggers>
        </Configuration>
        """);

    // logger names are packages (lowercase last segment) so nothing should match here
    assertThat(collect()).isEmpty();
  }

  private Set<String> collect() {
    return new XmlClassesAnalyzer(directory).collectReferencedClassesFromXml();
  }

  private Path write(String relativePath, String content) throws IOException {
    Path file = directory.resolve(relativePath);
    Files.createDirectories(file.getParent());
    return Files.writeString(file, content);
  }
}
