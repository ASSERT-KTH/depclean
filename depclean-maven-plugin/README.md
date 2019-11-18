# Bloated Dependencies in the Maven Ecosystem

### What is `jdbl-pom-maven-plugin`?

`jdbl-pom-maven-plugin` is a Maven plugin to automatically remove dependencies that are included in the Maven dependency tree but are not actually used in the Java projects. This tool detects and removes all the unused dependencies declared in the `pom.xml` file of a project or imported from its parent. For that, it relies on bytecode static analysis and the `maven-dependency-analyze` plugin. The tool does not modify the original source code of the application nor its original `pom.xml`. It can be executed as a Maven goal through the command line or integrated directly into the Maven build lifecycle.

### How does `jdbl-pom-maven-plugin` work?

`jdbl-pom-maven-plugin` runs before executing the `package` phase of the Maven build lifecycle. It statically collects all the types referenced in the project under analysis as well as in its declared dependencies. Then, it compares the types that the project actually use in the bytecode with respect to the class members belonging to its dependencies.

With the usage information, the `jdbl-pom-maven-plugin` executes the following steps:

1. add all used transitive dependencies as direct dependencies to the `pom`
2. remove all bloated direct dependencies from the `pom`
3. exclude all bloated transitive dependencies in the `pom`

If all the tests pass and the project builds correctly after this changes, then it means that the identified  as bloated dependencies can be removed. `jdbl-pom-maven-plugin` produces a file named `pom-debloated.xml`, located in the root of the project, which is a clean version of the original `pom.xml` without bloated dependencies.

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

Once the plugin is installed, you can execute the plugin directly in the command line:

```shell script
mvn se.kth.jdbl:debloat-pom-maven-plugin:0.1.0-SNAPSHOT:debloat-pom
```

Or you can configure the `pom.xml` file of your Maven project to use `jdbl-pom-maven-plugin` as part of the build:

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
