# DepClean <img src="https://github.com/castor-software/depclean/blob/master/logo.svg" align="right" height="139px" alt="DepClean logo"/>

[![Build Status](https://travis-ci.org/castor-software/depclean.svg?branch=master)](https://travis-ci.org/castor-software/depclean)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=castor-software_depclean&metric=alert_status)](https://sonarcloud.io/dashboard?id=castor-software_depclean)
[![Maven Central](https://img.shields.io/maven-central/v/se.kth.castor/depclean-core.svg)](https://search.maven.org/search?q=g:se.kth.castor%20AND%20a:depclean*)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=castor-software_depclean&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=castor-software_depclean)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=castor-software_depclean&metric=reliability_rating)](https://sonarcloud.io/dashboard?id=castor-software_depclean)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=castor-software_depclean&metric=security_rating)](https://sonarcloud.io/dashboard?id=castor-software_depclean)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=castor-software_depclean&metric=vulnerabilities)](https://sonarcloud.io/dashboard?id=castor-software_depclean)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=castor-software_depclean&metric=bugs)](https://sonarcloud.io/dashboard?id=castor-software_depclean)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=castor-software_depclean&metric=code_smells)](https://sonarcloud.io/dashboard?id=castor-software_depclean)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=castor-software_depclean&metric=ncloc)](https://sonarcloud.io/dashboard?id=castor-software_depclean)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=castor-software_depclean&metric=duplicated_lines_density)](https://sonarcloud.io/dashboard?id=castor-software_depclean)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=castor-software_depclean&metric=sqale_index)](https://sonarcloud.io/dashboard?id=castor-software_depclean)

<!--
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=castor-software_depclean&metric=coverage)](https://sonarcloud.io/dashboard?id=castor-software_depclean)
-->

## What is DepClean?

DepClean is a tool to automatically remove dependencies that are included in your Java dependency tree but are not actually used in the project's code. DepClean detects and removes all the unused dependencies declared in the `pom.xml` file of a project or imported from its parent. For that, it relies on bytecode static analysis and extends the `maven-dependency-analyze` plugin (more details on this [plugin](https://maven.apache.org/plugins/maven-dependency-plugin/analyze-mojo.html)). DepClean does not modify the original source code of the application nor its original `pom.xml`. It can be executed as a Maven goal through the command line or integrated directly into the Maven build lifecycle.

## How does it work?

DepClean runs before executing the `package` phase of the Maven build lifecycle. It statically collects all the types referenced in the project under analysis as well as in its declared dependencies. Then, it compares the types that the project actually use in the bytecode with respect to the class members belonging to its dependencies.

With this usage information, DepClean constructs a new `pom.xml` based on the following steps:

1. add all used transitive dependencies as direct dependencies
2. remove all unused direct dependencies
3. exclude all unused transitive dependencies

If all the tests pass and the project builds correctly after these changes, then it means that the dependencies identified as bloated can be removed. DepClean produces a file named `pom-debloated.xml`, located in the root of the project, which is a clean version of the original `pom.xml` without bloated dependencies.


## Usage

You can configure the `pom.xml` file of your Maven project to use DepClean as part of the build:

```xml
<plugin>
    <groupId>se.kth.castor</groupId>
    <artifactId>depclean-maven-plugin</artifactId>
    <version>1.1.0</version>
    <executions>
        <execution>
            <goals>
                <goal>depclean</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Optional Parameters

The Maven plugin can be configured with the following additional parameters.

| Name   |  Type |   Description      | 
|:----------|:-------------:| :-------------| 
| `<ignoreDependencies>` | `Set<String>` | Add a list of dependencies, identified by their coordinates, to be ignored by DepClean during the analysis and considered as used dependencies. Useful to override incomplete result caused by bytecode-level analysis. **Dependency format is:** `groupId:artifactId:version`.|
| `<ignoreScopes>` | `Set<String>` | Add a list of scopes, to be ignored by DepClean during the analysis. Useful to not analyze dependencies with scopes that are not needed at runtime. **Valid scopes are:** `compile`, `provided`, `test`, `runtime`, `system`, `import`. An Empty string indicates no scopes (default).|
| `<createPomDebloated>` | `boolean` | If this is true, DepClean creates a debloated version of the pom without unused dependencies called `debloated-pom.xml`, in root of the project. **Default value is:** `false`.|
| `<failIfUnusedDependency>` | `boolean` | If this is true, and DepClean reported any unused dependency in the dependency tree, the build fails immediately after running DepClean. **Default value is:** `false`.|
| `<skipDepClean>` | `boolean` | Skip plugin execution completely. **Default value is:** `false`.|




## Installing and building from source

Prerequisites:

- [Java OpenJDK 8](https://openjdk.java.net) or above
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
Once the plugin is installed, you can execute the plugin goal directly in the command line:

```shell script
mvn se.kth.castor:depclean-maven-plugin:1.0.0:depclean
```

## License

Distributed under the MIT License. See [LICENSE](https://github.com/castor-software/depclean/blob/master/LICENSE.md) for more information.

## Funding

DepClean is partially funded by the [Wallenberg Autonomous Systems and Software Program (WASP)](https://wasp-sweden.org).

<img src="https://github.com/castor-software/depclean/blob/master/wasp.svg" height="50px" alt="Wallenberg Autonomous Systems and Software Program (WASP)"/>
