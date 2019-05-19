# JDbl-pom: Automatic Detection and Removal of Unused Maven Dependencies

### What is JDbl-pom?

JDbl-pom is a tool to automatically remove dependencies that are declared but unused in Java projects that build with Maven. It detects and removes all the unused dependencies declared in the parent `pom.xml` file and their sub-modules. This tool relies on static analysis and do not modify the original source code of the application. It can be integrated directly into the Maven build lifecycle as a plugin.

### How does JDbl-pom work?

JDbl-pom acts before executing the packaging phase of the Maven build lifecycle. It statically collects all the types referenced in the project under analysis as well as in its declared dependencies. Then it compares the types that the project actually use with respect to the types belonging to its dependencies. If a dependency has none of its types used, then Jdbl-pom temporarily removes it and runs the test suite as a sanity check. If the project builds correctly without such dependency then it means that the dependency can be removed. As a result, JDbl-pom produces a file named `jdbl-pom.xml` in the root of the project which is clean and free of bloated dependencies.

## Getting Started

### Prerequisites

- [Java OpenJDK 8](https://openjdk.java.net) or above
- [Apache Maven](https://maven.apache.org/)

### Installing and building from source

In a terminal clone the repository:

```bash
git clone https://github.com/castor-software/royal-debloat.git
```
switch to the cloned folder:

```bash
cd jdbl-pom
```
and run the following Maven command to build the application and install the plugin locally:

```bash
mvn clean install
```
### Usage

Once the plugin is installed, configure the `pom.xml` file to include it as part of the build of the Maven project:

```xml
<plugin>
    <groupId>se.kth.jdbl</groupId>
    <artifactId>jdbl-pom</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <executions>
        <execution>
        <goals>
            <goal>pom-debloat</goal>
        </goals>
        </execution>
    </executions>
</plugin>
```

## License

Distributed under the MIT License. See [LICENSE](https://github.com/castor-software/royal-debloat/blob/master/LICENSE) for more information.


