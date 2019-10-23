# `jdbl-pom-maven-plugin`: Automatic Detection and Removal of Bloated Maven Dependencies

### What is `jdbl-pom-maven-plugin`?

`jdbl-pom-maven-plugin` is a Maven plugin to automatically remove dependencies that are declared but not used in Java projects. This tool detects and removes all the unused dependencies declared in the `pom.xml` file of a project. For that, it relies on bytecode static analysis and do not modify the original source code of the application. It can be integrated directly into the Maven build lifecycle.

### How does `jdbl-pom-maven-plugin` work?

`jdbl-pom-maven-plugin` runs before executing the `package` phase of the Maven build lifecycle. It statically collects all the types referenced in the project under analysis as well as in its declared dependencies. Then it compares the types that the project actually use in the bytecode with respect to the class members belonging to its dependencies. If a dependency has none of its members used, then Jdbl-pom temporarily removes it and runs the test suite as a sanity check. If all the tests pass and the project builds correctly without such dependency then it means that the dependency can be removed. `jdbl-pom-maven-plugin` produces a file named `pom-debloated.xml`, located in the root of the project, which is clean and free of bloated dependencies.

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

Once the plugin is installed, configure the `pom.xml` file of your Maven project to use `jdbl-pom-maven-plugin` as part of the build:

```xml
<plugin>
    <groupId>se.kth.jdbl</groupId>
    <artifactId>debloat-pom-maven-plugin</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <executions>
        <execution>
            <goals>
                <goal>debloat-pom</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

## License

Distributed under the MIT License. See [LICENSE](https://github.com/castor-software/royal-debloat/blob/master/LICENSE) for more information.
