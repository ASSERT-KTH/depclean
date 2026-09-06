## DepClean Gradle Plugin

The DepClean Gradle plugin is designed to automatically detect and remove unused dependencies in Gradle-based Java projects.
It uses `depclean-core` for the heavy bytecode analysis tasks, and provides a Gradle task that reports the unused dependencies of the project and can write a debloated `dependencies { }` block to replace the one in your `build.gradle` file. It never modifies your `build.gradle` itself.
As with the DepClean Maven plugin, this is a powerful tool to keep your project lean, avoid unnecessary compilation and potential conflicts or security vulnerabilities due to bloated dependencies.

### Prototype Stage

**This is currently a prototype project**. 
It means the DepClean Gradle plugin has undergone initial development and testing to demonstrate its feasibility.
While it has shown promising results, the tool is not yet mature for production usage, and its performance may not be optimal.

### Usage

Once you have the plugin installed in your local Maven repository (see [Installing and Building From Source](#installing-and-building-from-source) below), you can use it in your Gradle projects.

Because the plugin is resolved from your local Maven repository, add `mavenLocal()` to the plugin repositories in your `settings.gradle`:

```groovy
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}
```

Then add the plugin to your `build.gradle` file:

```groovy
plugins {
    id 'se.kth.castor.depclean-gradle-plugin' version '2.2.1-SNAPSHOT'
}
```
Then, you can run the `debloat` task to analyze your project and remove unused dependencies:

```bash
./gradlew debloat
```

### Optional Parameters

The plugin is configured through the `depclean { }` extension in `build.gradle`, for example:

```groovy
depclean {
    createBuildDebloated = true
    createResultJson = true
    ignoreConfiguration = ['testCompile']
}
```

The class [DepCleanGradlePluginExtension.java](https://github.com/ASSERT-KTH/depclean/blob/master/depclean-gradle-plugin/src/main/java/se/kth/depclean/DepCleanGradlePluginExtension.java) contains the following parameters currently accepted by DepClean Gradle plugin (all `false`/unset by default):

- `project`: The Gradle project to analyze. It defaults to the project the plugin is applied to and normally does not need to be set.
- `skipDepClean`: If this is set to true, the execution of the DepClean plugin will be completely skipped.
- `ignoreTest`: When this parameter is set to true, DepClean will not analyze the test sources in the project. Dependencies only used for testing will be considered unused.
- `failIfUnusedDirect`: If set to true, and if DepClean identifies any unused direct dependency, the project's build will fail immediately.
- `failIfUnusedTransitive`: Similar to `failIfUnusedDirect`, but in this case, the build will fail if any unused transitive dependencies are identified.
- `failIfUnusedInherited`: If true and DepClean finds any unused inherited dependency, the build fails immediately.
- `createBuildDebloated`: If set to `true`, it will generate a `debloated-dependencies.gradle` file in the project directory containing a `dependencies { }` block without the unused dependencies (used transitive dependencies are added as direct ones, unused transitive dependencies are excluded).
- `createResultJson`: When this is `true`, DepClean generates a JSON file with the results of the analysis, named `depclean-results.json`, in the `build` directory.
- `createClassUsageCsv`: If this is set to `true`, it generates a CSV file with the result of the analysis, including the columns: `OriginClass`, `TargetClass`, and `Dependency`. The file is named `class-usage.csv` and is located in the `build` directory. It is only written together with the JSON report, so `createResultJson` must be `true` as well.
- `ignoreConfiguration`: A set of configuration names (as printed in the analysis results, e.g. `compile`, `testCompile`) whose dependencies are excluded from the DepClean results.
- `ignoreDependency`: A set of dependencies that should be ignored by the plugin during the analysis and considered as used dependencies. Each entry must match a coordinate exactly as printed in the analysis results, i.e. `group:name:version:configuration`.

### Looking for Contributors

We are actively seeking contributions to help move this project forward. If you're experienced in Gradle, Java, and have an interest in software quality, we'd love your help. There are various ways to contribute:

- **Code contributions**: If you're a developer and want to contribute, feel free to submit a pull-request. Be sure to check our open issues. If there's something you want to work on, leave a comment, or you can open your own issue describing the change you're proposing.
- **Bug reports**: If you find a bug while using the plugin, please report it in our issue tracker.
- **Feature suggestions**: If you think of a feature that would enhance the plugin, we'd love to hear about it! You can submit it as an issue with the tag "enhancement".
- **Testing and feedback**: Any feedback you can provide as to how the plugin works in your own projects would be invaluable. If you can test the plugin and let us know what you think, we would appreciate it.
- **Spread the word**: The more people know about our project, the more great contributions we can get. So, please, share it with your peers!

## Installing and Building From Source

Prerequisites:

- Java OpenJDK 21 or above (the plugin targets Java 17 bytecode)
- No Gradle installation needed — the Gradle wrapper is included

In a terminal clone the repository and switch to the cloned folder:

```bash
git clone https://github.com/ASSERT-KTH/depclean.git
cd depclean
```

First build and install the DepClean core modules into your local Maven repository:

```bash
./mvnw clean install -DskipTests
```

Then build the Gradle plugin and publish it to your local Maven repository:

```bash
cd depclean-gradle-plugin
./gradlew publishToMavenLocal
```