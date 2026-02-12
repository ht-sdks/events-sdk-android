# Agent Instructions

This file provides instructions for AI agents working on this Android SDK repository.

## Project Overview

- **Language**: Java / Kotlin
- **Platform**: Android (minSdk 14, targetSdk 32, compileSdk 32)
- **Build System**: Gradle 7.3.3 with Gradle Wrapper
- **JDK**: 11 (required)
- **Kotlin Version**: 1.4.0
- **Testing**: JUnit 4 + Robolectric
- **Publishing**: JitPack

### Project Structure

```
analytics/                        # Core analytics library (published)
analytics-tests/                  # Integration/instrumentation tests
analytics-wear/                   # Android Wear support (published)
analytics-samples/
  analytics-sample/               # Sample app (Java)
  analytics-wear-sample/          # Sample Wear app
  kotlin-sample/                  # Sample app (Kotlin)
gradle/
  android.gradle                  # Shared Android config
  versioning.gradle               # Version name/code logic
  publish-local.gradle            # Local Maven publishing
  mvn-publish.gradle              # Maven Central publishing
  attach-jar.gradle               # JAR attachment config
  promote.gradle                  # Promotion config
```

### Module Dependencies

```
analytics-tests ──► analytics
analytics-wear ──► analytics
analytics-samples/* ──► analytics
```

---

## Updating Dependencies

### 1. Pre-flight Checks

```bash
# Verify JDK 11 is available
java -version

# Ensure you're at the repository root
pwd  # Should be: /path/to/events-sdk-android

# Verify the Gradle wrapper is executable
chmod +x gradlew

# Confirm Gradle version
./gradlew --version
```

### 2. Establish Test Baseline

```bash
# Run all checks, build, and assemble Android tests (mirrors CI)
./gradlew check build assembleAndroidTest
```

Record the number of passing tests before making any changes. This ensures you can verify nothing broke after upgrading. Look for the test summary in the output, e.g. in `analytics/build/reports/tests/testReleaseUnitTest/index.html`.

### 3. Check for Security Advisories

```bash
# List all dependencies and their versions for manual review
./gradlew dependencies

# For the core analytics module specifically
./gradlew :analytics:dependencies
```

Review dependency versions against known CVE databases (e.g., [OSV](https://osv.dev/), [NVD](https://nvd.nist.gov/)). Gradle does not have a built-in `audit` command, so manual review or third-party plugins (e.g., `org.owasp.dependencycheck`) are needed.

### 4. Check Outdated Packages

```bash
# Show all resolved dependencies for the analytics module
./gradlew :analytics:dependencies --configuration releaseRuntimeClasspath

# Show all resolved dependencies for the test module
./gradlew :analytics-tests:dependencies --configuration releaseRuntimeClasspath
```

Compare each dependency version against its latest release on Maven Central or Google's Maven Repository. Key dependencies to watch:

| Dependency | Current | Where Defined |
|---|---|---|
| `com.android.tools.build:gradle` | 7.2.2 | `build.gradle` (root) |
| `org.jetbrains.kotlin:kotlin-gradle-plugin` | 1.4.0 | `build.gradle` (root) |
| `androidx.annotation:annotation` | 1.1.0 | `build.gradle` (root `ext.deps`) |
| `androidx.lifecycle:lifecycle-process` | 2.2.0 | `analytics/build.gradle` |
| `androidx.lifecycle:lifecycle-common-java8` | 2.2.0 | `analytics/build.gradle` |
| `androidx.core:core-ktx` | 1.3.1 | `analytics/build.gradle` |
| `org.robolectric:robolectric` | 4.7.3 | `analytics/build.gradle` (test) |
| `junit:junit` | 4.13.2 | `analytics/build.gradle` (test) |
| `com.google.android.gms:play-services-wearable` | 10.2.6 | `analytics-wear/build.gradle` |

### 5. Upgrade Dependencies

#### Option A: Safe Updates (patch/minor within expected compatibility)

Edit the version strings directly in the relevant `build.gradle` files:

- **Root `build.gradle`**: Android Gradle Plugin, Kotlin plugin, shared dependency versions in `ext.deps`
- **`analytics/build.gradle`**: AndroidX libraries, test dependencies
- **`analytics-wear/build.gradle`**: Play Services Wearable
- **`gradle/wrapper/gradle-wrapper.properties`**: Gradle wrapper version

After editing, sync and rebuild:

```bash
./gradlew clean build
```

#### Option B: Major Version Updates

For major version bumps (e.g., Kotlin 1.x to 2.x, AGP major update):

1. Edit version strings in `build.gradle` files
2. Update `gradle-wrapper.properties` if Gradle itself needs upgrading:

```bash
# Update Gradle wrapper to a specific version
./gradlew wrapper --gradle-version=<NEW_VERSION>
```

3. Clean and rebuild from scratch:

```bash
./gradlew clean
./gradlew check build assembleAndroidTest
```

#### Option C: Using Gradle Version Catalog (future)

This project does not yet use a Gradle Version Catalog (`libs.versions.toml`). If migrating to one, centralize all versions there and reference them in `build.gradle` files.

### 6. Rebuild and Test

```bash
# Clean build to ensure no stale artifacts
./gradlew clean

# Run the full CI pipeline locally
./gradlew check build assembleAndroidTest
```

Compare test results to the baseline from Step 2. Fix any failures before proceeding.

### 7. Verify CI Would Pass

The CI workflow (`.github/workflows/android.yml`) runs the following on Ubuntu with JDK 11 (Temurin):

```bash
chmod +x gradlew
./gradlew check build assembleAndroidTest
```

Ensure this exact command passes locally before pushing. The `check` task runs lint and unit tests. The `build` task compiles all modules. The `assembleAndroidTest` task compiles Android instrumentation tests.

---

## Module-Specific Notes

### Core Analytics (`analytics/`)

The main library module. This is the primary published artifact. Update this first when doing cross-module dependency upgrades.

```bash
./gradlew :analytics:check :analytics:build
```

Key files:
- `analytics/build.gradle` - dependencies and build config
- `analytics/src/main/` - source code (Java + Kotlin)
- `analytics/src/test/` - unit tests (Robolectric)

### Analytics Tests (`analytics-tests/`)

Integration tests module that depends on `analytics`.

```bash
./gradlew :analytics-tests:check :analytics-tests:build
```

### Analytics Wear (`analytics-wear/`)

Android Wear support module. Depends on `analytics` and Google Play Services Wearable.

```bash
./gradlew :analytics-wear:check :analytics-wear:build
```

### Sample Apps (`analytics-samples/`)

Sample applications demonstrating SDK usage. Not published but useful for manual verification.

```bash
./gradlew :analytics-samples:analytics-sample:assembleDebug
./gradlew :analytics-samples:kotlin-sample:assembleDebug
```

---

## Version Bumping

### Semantic Versioning

- **PATCH** (0.0.7 → 0.0.8): Bug fixes, dependency updates, no new features
- **MINOR** (0.0.7 → 0.1.0): New backwards-compatible features
- **MAJOR** (0.0.7 → 1.0.0): Breaking API changes

Dependency updates are typically **PATCH** bumps.

### Files to Update

1. `gradle.properties` → `VERSION_NAME` and `VERSION_CODE`
2. The `BuildConfig.VERSION_NAME` field is auto-generated from `gradle.properties` during build

See `RELEASING.md` for the full release process via JitPack.

---

## CI/CD

- **CI config**: `.github/workflows/android.yml`
- **Runs on**: Ubuntu (latest) with JDK 11 (Temurin distribution)
- **Steps**: `chmod +x gradlew`, `./gradlew check build assembleAndroidTest`
- **Triggers**: Push to `main`, pull requests targeting `main`

### CI Failures After Dependency Updates

1. **Compilation errors**: Check for API changes in updated libraries (e.g., removed or renamed methods)
2. **Test failures**: Review changelogs of updated dependencies for behavior changes
3. **Lint errors**: The `check` task includes Android Lint. New lint rules may flag existing code after AGP updates
4. **Robolectric issues**: Robolectric version must be compatible with the target SDK version. Check the [Robolectric compatibility matrix](http://robolectric.org/getting-started/)

---

## Common Issues

### Gradle Wrapper Permissions

If `./gradlew` fails with a permission error:

```bash
chmod +x gradlew
```

### JDK Version Mismatch

This project requires JDK 11. If you see errors about unsupported class file versions or missing APIs:

```bash
# Check current JDK
java -version

# Set JAVA_HOME if needed
export JAVA_HOME=/path/to/jdk-11
```

### AndroidX / Jetifier

The project uses AndroidX with Jetifier enabled (`android.enableJetifier=true` in `gradle.properties`). If adding new dependencies, ensure they are AndroidX-compatible or can be jetified.

### Robolectric Download Issues

Robolectric downloads Android SDK jars on first run. If behind a proxy or firewall, you may see download failures. The repo URL is configured in `gradle/android.gradle`:

```groovy
systemProperty 'robolectric.dependency.repo.url', 'https://repo1.maven.org/maven2'
```

### Kotlin Version Compatibility

The project uses Kotlin 1.4.0. When upgrading Kotlin:

1. Update `ext.kotlin_version` in root `build.gradle`
2. Ensure the Kotlin stdlib version matches across all modules
3. Check for deprecation warnings that may become errors in newer Kotlin versions

### Android Gradle Plugin (AGP) Upgrades

AGP version must be compatible with the Gradle wrapper version. See the [AGP/Gradle compatibility matrix](https://developer.android.com/build/releases/gradle-plugin#updating-gradle). Current: AGP 7.2.2 requires Gradle 7.3.3+.

---

## Quick Reference

| Task | Command |
|------|---------|
| Full CI build | `./gradlew check build assembleAndroidTest` |
| Clean build | `./gradlew clean build` |
| Run unit tests only | `./gradlew test` |
| Run lint checks | `./gradlew lint` |
| Build core library | `./gradlew :analytics:build` |
| Build wear library | `./gradlew :analytics-wear:build` |
| List dependencies | `./gradlew :analytics:dependencies` |
| Publish to local Maven | `./gradlew publishToMavenLocal` |
| Assemble debug samples | `./gradlew :analytics-samples:analytics-sample:assembleDebug` |
| Check Gradle version | `./gradlew --version` |
| Update Gradle wrapper | `./gradlew wrapper --gradle-version=<VERSION>` |
