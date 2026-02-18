# Agent Instructions

This file provides guidance for AI agents updating dependencies in this Android SDK repository.

## Project Overview

- **Language**: Java / Kotlin
- **Build System**: Gradle 7.3.3 with Gradle Wrapper
- **JDK**: 11 (required)
- **Testing**: JUnit 4 + Robolectric
- **CI command**: `./gradlew check build assembleAndroidTest`

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

Compare each dependency version against its latest release on Maven Central or Google's Maven Repository. Dependency versions are defined in these locations:

- **Root `build.gradle`**: Android Gradle Plugin, Kotlin plugin version (`ext.kotlin_version`), shared dependency versions (`ext.deps`)
- **`analytics/build.gradle`**: AndroidX libraries, test dependencies (Robolectric, JUnit, Mockito, etc.)
- **`analytics-wear/build.gradle`**: Play Services Wearable
- **`gradle/wrapper/gradle-wrapper.properties`**: Gradle wrapper version

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

## Troubleshooting Dependency Updates

### CI Failures After Upgrades

1. **Compilation errors**: Check for API changes in updated libraries (e.g., removed or renamed methods)
2. **Test failures**: Review changelogs of updated dependencies for behavior changes
3. **Lint errors**: The `check` task includes Android Lint. New lint rules may flag existing code after AGP updates
4. **Robolectric issues**: Robolectric version must be compatible with the target SDK version. Check the [Robolectric compatibility matrix](http://robolectric.org/getting-started/)

### Kotlin Version Compatibility

When upgrading Kotlin:

1. Update `ext.kotlin_version` in root `build.gradle`
2. Ensure the Kotlin stdlib version matches across all modules
3. Check for deprecation warnings that may become errors in newer Kotlin versions

### Android Gradle Plugin (AGP) Upgrades

AGP version must be compatible with the Gradle wrapper version. See the [AGP/Gradle compatibility matrix](https://developer.android.com/build/releases/gradle-plugin#updating-gradle). Current: AGP 7.2.2 requires Gradle 7.3.3+.
