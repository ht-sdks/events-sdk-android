# Releasing

## JitPack Release

1. Increment version name and version code in gradle.properties
1. Merge to `main`
1. Tag commit on `main` using the version name (e.g. semantic versioning X.Y.Z)
1. Push tags to github
1. Visit https://jitpack.io/#ht-sdks/events-sdk-android/ and refresh until the `Version` tab shows your new tag. Click `Get it`. Click the spinner next to `Get it` and wait for the request to return the build.log. Ensure that the build succeeded and created new assets with your desired tags.

## Local Release Testing

1. Increment version name and version code in gradle.properties
1. In android studio, run the gradle for `publishToMavenLocal`
1. This should create a `com.hightouch` package at `~./m2/repositories`
1. Change your test android app's gradle dependencies in `build.gradle` (not this repository's) from

```gradle
dependencies {
  implementation("com.github.ht-sdks.events-sdk-android:analytics:X.Y.Z")
}
```

to point at your local package (swap out the X.Y.Z for your version)

```gradle
dependencies {
  implementation("com.hightouch.analytics.android:analytics:X.Y.Z")
}
```

You'll also need to then set up local maven dependencies.

```
  allprojects {
    repositories {
      ...
      mavenLocal()
    }
  }
```

## Snapshot

In addition to git's tagged versions, you can point JitPack at specifc commits or branches.
