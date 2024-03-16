# DepClean <img src="https://github.com/castor-software/depclean/blob/master/.img/logo.svg" align="left" height="135px" alt="DepClean logo"/>

[![build](https://github.com/castor-software/depclean/actions/workflows/build.yml/badge.svg)](https://github.com/castor-software/depclean/actions/workflows/build.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=ASSERT-KTH_depclean&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=ASSERT-KTH_depclean)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=ASSERT-KTH_depclean&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=ASSERT-KTH_depclean)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=ASSERT-KTH_depclean&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=ASSERT-KTH_depclean)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=ASSERT-KTH_depclean&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=ASSERT-KTH_depclean)
[![Maven Central](https://img.shields.io/maven-central/v/se.kth.castor/depclean-core.svg)](https://search.maven.org/search?q=g:se.kth.castor%20AND%20a:depclean*)
[![Licence](http://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/castor-software/depclean/blob/master/LICENSE.md)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=ASSERT-KTH_depclean&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=ASSERT-KTH_depclean)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=ASSERT-KTH_depclean&metric=bugs)](https://sonarcloud.io/summary/new_code?id=ASSERT-KTH_depclean)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=ASSERT-KTH_depclean&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=ASSERT-KTH_depclean)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=ASSERT-KTH_depclean&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=ASSERT-KTH_depclean)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=ASSERT-KTH_depclean&metric=duplicated_lines_density)](https://sonarcloud.io/summary/new_code?id=ASSERT-KTH_depclean)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=ASSERT-KTH_depclean&metric=sqale_index)](https://sonarcloud.io/summary/new_code?id=ASSERT-KTH_depclean)
[![codecov](https://codecov.io/gh/ASSERT-KTH/depclean/graph/badge.svg?token=X0XE6R72OD)](https://codecov.io/gh/ASSERT-KTH/depclean)

## What is DepClean?

DepClean automatically cleans the dependency tree of Java projects.
It removes all the dependencies that are included in the project's dependency tree but are not actually necessary to build it. 
DepClean detects and removes all the unused dependencies declared in the `pom.xml` file of a project or imported from its parent. 
It can be executed as a Maven goal through the command line or integrated directly into the Maven build lifecycle (CI/CD).
DepClean does not modify the original source code of the application nor its original `pom.xml`. It has been presented in ["A Comprehensive Study of Bloated Dependencies in the Maven Ecosystem](http://arxiv.org/pdf/2001.07808") ([doi:10.1007/s10664-020-09914-8](https://doi.org/10.1007/s10664-020-09914-8)).

For a visual illustration of what DepClean can provide for your project, have a look at the companion [depclean-web](https://github.com/castor-software/depclean-web) project.

## Usage

Configure the `pom.xml` file of your Maven project to use DepClean as part of the build:

```xml
<plugin>
  <groupId>se.kth.castor</groupId>
  <artifactId>depclean-maven-plugin</artifactId>
  <version>2.0.6</version>
  <executions>
    <execution>
      <goals>
        <goal>depclean</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

Or you can run DepClean directly from the command line.

```bash
cd {PATH_TO_MAVEN_PROJECT}
mvn compile
mvn compiler:testCompile
mvn se.kth.castor:depclean-maven-plugin:2.0.6:depclean
```

Let's see an example of running DepClean version 2.0.1 in the project [Apache Commons Numbers](https://github.com/apache/commons-numbers/tree/master/commons-numbers-examples/examples-jmh)!

![Demo](https://github.com/castor-software/depclean/blob/master/.img/demo.gif)

## Optional Parameters

The Maven plugin can be configured with the following additional parameters.


| Name                       |     Type      | Description                                                                                                                                                                                                                                                                                                                                                                                                               | 
|:---------------------------|:-------------:|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------| 
| `<ignoreDependencies>`     | `Set<String>` | A regex expression matching dependencies to be ignored by DepClean during the analysis (i.e., considered as used). This is useful to bypass incomplete result caused by bytecode-level analysis. **For example:** `-DignoreDependencies="net.bytebuddy:byte-buddy:.*","com.google.guava.*"` ignores dependencies with `groupId:artifactId` equals to `net.bytebuddy:byte-buddy` and `groupId` equals to `com.google.guava`. |
| `<ignoreScopes>`           | `Set<String>` | Add a list of scopes, to be ignored by DepClean during the analysis. Useful to not analyze dependencies with scopes that are not needed at runtime. **Valid scopes are:** `compile`, `provided`, `test`, `runtime`, `system`, `import`. An Empty string indicates no scopes (default).                                                                                                                                    |
| `<ignoreTests>`            |   `boolean`   | If this is true, DepClean will not analyze the test classes in the project, and, therefore, the dependencies that are only used for testing will be considered unused. This parameter is useful to detect dependencies that have `compile` scope but are only used for testing. **Default value is:** `false`.                                                                                                            |
| `<createPomDebloated>`     |   `boolean`   | If this is true, DepClean creates a debloated version of the pom without unused dependencies called `debloated-pom.xml`, in the root of the project. **Default value is:** `false`.                                                                                                                                                                                                                                       |
| `<createResultJson>`       |   `boolean`   | If this is true, DepClean creates a JSON file of the dependency tree along with metadata of each dependency. The file is called `depclean-results.json`, and is located in the `target` directory of the project. **Default value is:** `false`.                                                                                                                                                                          |
| `<createCallGraphCsv>`     |   `boolean`   | If this is true, DepClean creates a CSV file with the static call graph of the API members used in the project. The file is called `depclean-callgraph.csv`, and is located in the `target` directory of the project. **Default value is:** `false`.                                                                                                                                                                      |
| `<failIfUnusedDirect>`     |   `boolean`   | If this is true, and DepClean reported any unused direct dependency in the dependency tree, the build fails immediately after running DepClean. **Default value is:** `false`.                                                                                                                                                                                                                                            |
| `<failIfUnusedTransitive>` |   `boolean`   | If this is true, and DepClean reported any unused transitive dependency in the dependency tree, the build fails immediately after running DepClean. **Default value is:** `false`.                                                                                                                                                                                                                                        |
| `<failIfUnusedInherited>`  |   `boolean`   | If this is true, and DepClean reported any unused inherited dependency in the dependency tree, the build fails immediately after running DepClean. **Default value is:** `false`.                                                                                                                                                                                                                                         |
| `<skipDepClean>`           |   `boolean`   | Skip plugin execution completely. **Default value is:** `false`.                                                                                                                                                                                                                                                                                                                                                          |


You can integrate DepClean in your CI/CD pipeline.
For example, if you want to fail the build in the presence of unused direct dependencies, while ignoring all the dependency scopes except the
`compile`, use the following plugin configuration.

```xml
<plugin>
  <groupId>se.kth.castor</groupId>
  <artifactId>depclean-maven-plugin</artifactId>
  <version>2.0.6</version>
  <executions>
    <execution>
      <goals>
        <goal>depclean</goal>
      </goals>
      <configuration>
        <failIfUnusedDirect>true</failIfUnusedDirect>
        <ignoreScopes>provided,test,runtime,system,import</ignoreScopes>
      </configuration>
    </execution>
  </executions>
</plugin>
```

Of course, it is also possible to execute DepClean with parameters directly from the command line. The previous example can be executed directly as follows:

```bash
mvn se.kth.castor:depclean-maven-plugin:2.0.6:depclean -DfailIfUnusedDirect=true -DignoreScopes=provided,test,runtime,system,import
```

## How does DepClean works?

DepClean runs before executing the `package` phase of the Maven build lifecycle. It statically collects all the types
referenced in the project under analysis as well as in its declared dependencies. Then, it compares the types that the
project actually use in the bytecode with respect to the class members belonging to its dependencies.

With this usage information, DepClean constructs a new `pom.xml` based on the following steps:

1. add all used transitive dependencies as direct dependencies
2. remove all unused direct dependencies
3. exclude all unused transitive dependencies

If all the tests pass, and the project builds correctly after these changes, then it means that the dependencies identified as bloated can be removed. DepClean produces a file named `pom-debloated.xml`, located in the root of the project, which is a clean version of the original `pom.xml` without bloated dependencies.

## Installing and building from source

Prerequisites:

- [Java OpenJDK 11](https://openjdk.java.net) or above
- [Apache Maven](https://maven.apache.org/)

In a terminal clone the repository and switch to the cloned folder:

```bash
git clone https://github.com/castor-software/depclean.git
cd depclean
```
Then run the following Maven command to build the application and install the plugin locally:

```bash
mvn clean install
```

## License

Distributed under the MIT License. See [LICENSE](https://github.com/castor-software/depclean/blob/master/LICENSE.md) for more information.

## Funding

DepClean is partially funded by the [Wallenberg Autonomous Systems and Software Program (WASP)](https://wasp-sweden.org).

<img src="https://github.com/castor-software/depclean/blob/master/.img/wasp.svg" height="50px" alt="Wallenberg Autonomous Systems and Software Program (WASP)"/>
