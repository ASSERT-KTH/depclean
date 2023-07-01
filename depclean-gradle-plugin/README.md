## DepClean Gradle Plugin

The DepClean Gradle plugin is designed to automatically detect and remove unused dependencies in Gradle-based Java projects.
It uses `depclean-core` for the heavy bytecode analysis tasks, and provides a Gradle task to remove unused dependencies from the project's `build.gradle` file.
Ad with the DepClean Maven plugin, this is a powerful tool to keep your project lean, avoid unnecessary compilation and potential conflicts or security vulnerabilities due to bloated dependencies.

### Prototype Stage

**This is currently a prototype project**. 
It means the DepClean Gradle plugin has undergone initial development and testing to demonstrate its feasibility.
While it has shown promising results, the tool is not yet mature for production usage, and its performance may not be optimal.

### Usage

Once you have the plugin installed in your local Maven repository, you can use it in your Gradle projects.

First, you need to add the plugin to your `build.gradle` file:

```groovy
plugins {
    id 'se.kth.depclean' version '0.1.0-SNAPSHOT'
}
```
Then, you can run the `debloat` task to analyze your project and remove unused dependencies:

```bash
./gradlew debloat
```

### Optional Parameters

The class [DepCleanGradlePluginExtension.java](https://github.com/ASSERT-KTH/depclean/blob/master/depclean-gradle-plugin/src/main/java/se/kth/depclean/DepCleanGradlePluginExtension.java) contains the following parameters currently accepted by DepClean Gradle plugin:
 
- `project`: This refers to the Gradle project that will be analyzed by the plugin.
- `skipDepClean`: If this is set to true, the execution of the DepClean plugin will be completely skipped.
- `ignoreTest`: When this parameter is set to true, DepClean will not analyze the test sources in the project. Dependencies only used for testing will be considered unused.
- `failIfUnusedDirect`: If set to true, and if DepClean identifies any unused direct dependency, the project's build will fail immediately.
- `failIfUnusedTransitive`: Similar to `failIfUnusedDirect`, but in this case, the build will fail if any unused transitive dependencies are identified.
- `failIfUnusedInherited`: If true and DepClean finds any unused inherited dependency, the build fails immediately.
- `createBuildDebloated`: If set to `true`, it will generate a debloated version of the `build.gradle` file without the unused dependencies, and name it `debloated-build.gradle`.
- `createResultJson`: When this is `true`, DepClean generates a JSON file with the results of the analysis, named `debloat-result.json`.
- `createClassUsageCsv`: If this is set to `true`, it generates a CSV file with the result of the analysis, including the columns: `OriginClass`, `TargetClass`, and `Dependency`. The file is named `class-usage.csv.
- `ignoreConfiguration`: This parameter allows you to ignore dependencies with specific configurations from the DepClean analysis.
- `ignoreDependency`: This parameter accepts a set of dependencies (identified by their coordinates) that should be ignored by the plugin during the analysis and considered as used dependencies.

### Looking for Contributors

We are actively seeking contributions to help move this project forward. If you're experienced in Gradle, Java, and have an interest in software quality, we'd love your help. There are various ways to contribute:

- **Code contributions**: If you're a developer and want to contribute, feel free to submit a pull-request. Be sure to check our open issues. If there's something you want to work on, leave a comment, or you can open your own issue describing the change you're proposing.
- **Bug reports**: If you find a bug while using the plugin, please report it in our issue tracker.
- **Feature suggestions**: If you think of a feature that would enhance the plugin, we'd love to hear about it! You can submit it as an issue with the tag "enhancement".
- **Testing and feedback**: Any feedback you can provide as to how the plugin works in your own projects would be invaluable. If you can test the plugin and let us know what you think, we would appreciate it.
- **Spread the word**: The more people know about our project, the more great contributions we can get. So, please, share it with your peers!

## Installing and Building From Source

Prerequisites:

- Java OpenJDK 11 or above
- Gradle 6.8.3 or above

In a terminal clone the repository and switch to the cloned folder:

```bash
git clone https://github.com/ASSERT-KTH/depclean.git
cd depclean-gradle-plugin
```
Then run the following Maven command to build the application and install the plugin locally:

```bash
./gradlew install
```