# Contributing to DepClean

Thanks for your interest in contributing! Bug reports, feature suggestions, documentation improvements, and pull requests are all welcome.

## Prerequisites

- Java (Open)JDK 21 or above — the Maven plugin's main sources target Java 8 bytecode (tests target Java 17), but building requires a modern JDK; CI builds with JDK 25
- No Maven or Gradle installation needed — both wrappers (`mvnw`, `gradlew`) are included

## Building the project

Clone the repository and build all Maven modules:

```bash
git clone https://github.com/ASSERT-KTH/depclean.git
cd depclean
./mvnw clean install
```

This is close to what CI runs (`./mvnw clean verify`) and executes unit tests, integration tests, and Checkstyle.

### Gradle plugin

The Gradle plugin (`depclean-gradle-plugin`) resolves `depclean-core` and its own snapshot from your **local Maven repository**, so build the Maven modules first, then publish and test the plugin:

```bash
./mvnw clean install -DskipTests
cd depclean-gradle-plugin
./gradlew clean publishToMavenLocal build
```

> **Note:** running `./gradlew test` without `publishToMavenLocal` tests the *stale* snapshot in `~/.m2`, not your current code. Always use the command above (it is what CI runs).

## Code style

- Java code follows a [Google Java Style](https://google.github.io/styleguide/javaguide.html)-based configuration enforced by Checkstyle ([checkstyle.xml](checkstyle.xml)). Checkstyle runs as part of the Maven build.
- The Gradle plugin module uses [Spotless](https://github.com/diffplug/spotless); run `./gradlew spotlessApply` to format.

## Tests

- Unit tests run with `./mvnw test`.
- The Maven plugin has integration tests (`DepCleanMojoIT`) that execute real Maven builds against fixture projects under `depclean-maven-plugin/src/test/resources-its`. Their assertions compare against golden log output, which embeds exact dependency versions and jar sizes — if you change a fixture, update the expected output from the actual logs under `depclean-maven-plugin/target/maven-it/`.
- Coverage is collected with JaCoCo and reported to [Codecov](https://codecov.io/gh/ASSERT-KTH/depclean).

## Submitting a pull request

Please follow the checklist in the [pull request template](.github/PULL_REQUEST_TEMPLATE/pull_request_template.md). In short:

1. Keep changes small and focused on a single problem (separate PRs for separate problems).
2. Run the build locally (`./mvnw clean verify`) and make sure it passes.
3. Reference the related issue in the PR title and description, and summarize your changes in bullet points.
4. Be ready to discuss your changes during code review.

## Releasing (maintainers only)

Releases are published to Maven Central through the [Central Publisher Portal](https://central.sonatype.com) (see the [Sonatype Maven guide](https://central.sonatype.org/publish/publish-portal-maven/)) by the [Deploy workflow](.github/workflows/deploy.yml).

1. Bump the version in `pom.xml` to the release version and merge it to `master`.
2. Run the **Deploy** workflow manually (Actions → Deploy → Run workflow), passing the previous and new versions.
3. The run pauses until a maintainer approves it in the `release` environment, then it signs the artifacts, publishes them, tags the commit, and opens a draft GitHub release.

The workflow needs these repository secrets:

| Secret | Purpose |
| --- | --- |
| `CENTRAL_TOKEN_USERNAME` | Username part of the Central Portal [user token](https://central.sonatype.org/publish/generate-portal-token/) |
| `CENTRAL_TOKEN_PASSWORD` | Password part of the Central Portal user token |
| `GPG_PRIVATE_KEY` | Private key used to sign the artifacts |
| `GPG_PASSPHRASE` | Passphrase of that key |

Central no longer accepts a Sonatype account password for publishing, so the token has to be regenerated from the Portal when it is rotated or revoked.

## Reporting bugs and requesting features

Use the [issue tracker](https://github.com/ASSERT-KTH/depclean/issues) with the provided templates. For security vulnerabilities, please follow the [security policy](SECURITY.md) instead of opening a public issue.
