# Releasing

## Maven Central + JitPack Release

1. Increment `VERSION_NAME` and `VERSION_CODE` in gradle.properties
1. Merge to `main`
1. Tag commit on `main` using the version name (e.g. semantic versioning X.Y.Z)
1. Push tags to github
1. The [tagged-release](.github/workflows/tagged-release.yml) workflow publishes to Maven Central
1. Confirm the version on the [Central Portal Deployments](https://central.sonatype.com/publishing) page, then on the [public artifact page](https://central.sonatype.com/artifact/com.hightouch.analytics.android/analytics) (may take ~15 minutes after release)

JitPack continues to build from tags on demand for consumers still on the old `com.github.ht-sdks.events-sdk-android` coordinates — no separate JitPack release step is required.

## Local Release Testing

1. Increment `VERSION_NAME` and `VERSION_CODE` in gradle.properties
1. In android studio, run the gradle for `publishToMavenLocal`
1. This should create packages under `~/.m2/repository/com/hightouch/analytics/android/`
1. In your test Android app, depend on the local package (same coordinates as Maven Central):

```gradle
dependencies {
  implementation 'com.hightouch.analytics.android:analytics:X.Y.Z'
}
```

Add `mavenLocal()` to that app's repositories (ahead of `mavenCentral()` if you need to prefer the local build):

```gradle
repositories {
  mavenLocal()
  mavenCentral()
}
```
