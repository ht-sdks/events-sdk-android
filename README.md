# Events SDK Android

## Installing the SDK

This SDK is published to [Maven Central](https://central.sonatype.com/artifact/com.hightouch.analytics.android/analytics).

```gradle
dependencies {
  implementation 'com.hightouch.analytics.android:analytics:X.Y.Z'
}
```

Replace `X.Y.Z` with the desired version. No extra repository block is needed if your project already includes `mavenCentral()`.

The SDK is also available through [JitPack](https://jitpack.io/#ht-sdks/events-sdk-android/) using `com.github.ht-sdks.events-sdk-android:analytics:X.Y.Z`. Prefer the Maven Central coordinates above for new integrations; JitPack builds from tags remain available for existing consumers.

## Initialization

Java:

```java
import com.hightouch.analytics.Analytics

public class MyApp extends Application {
  @Override public void onCreate() {

    Analytics analytics = new Analytics.Builder(
      this, // e.g. context
      "<WRITE_KEY>"
    )
    .defaultApiHost("<API_HOST>/v1")
    .trackApplicationLifecycleEvents()
    .build();

    Analytics.setSingletonInstance(analytics);

    // Safely call Analytics.with(context) from anywhere within your app!
    Analytics.with(this).track("Application Started");
  }
}
```

Kotlin

```kotlin
class MyApp : Application() {
  override fun onCreate() {
    val analytics = Analytics
        .Builder(
            this, // e.g. context
           "<WRITE_KEY>"
        )
        .defaultApiHost("<API_HOST>/v1")
        .trackApplicationLifecycleEvents()
        .build()

    Analytics.setSingletonInstance(analytics)

    // Safely call Analytics.with(context) from anywhere within your app!
    Analytics.with(this).track("Application Started")
  }
}
```

## Permissions

Update your application’s AndroidManifest.xml

```xml
<!-- Required for internet. -->
<uses-permission android:name="android.permission.INTERNET"/>
```

## Sending Events

### Identify

Java

```java
Analytics.with(context).identify("a user's id", new Traits().putName("John Doe"), null);
```

Kotlin

```kotlin
Analytics.with(context).identify("a user's id", Traits().putName("John Doe"), null)
```

### Track

Java

```java
Analytics.with(context).track(
  "Purchased Item", new Properties().putValue("sku", "13d31").putRevenue(199.99)
);
```

Kotlin

```kotlin
Analytics.with(context).track(
  "Purchased Item", Properties().putValue("sku", "13d31").putRevenue(199.99)
)
```

### Screen

Java

```java
// category "Feed" and a property "Feed Length"
Analytics.with(context).screen("Feed", new Properties().putValue("Feed Length", "26"));

// no category, name "Photo Feed" and a property "Feed Length"
Analytics.with(context).screen(null, "Photo Feed", new Properties().putValue("Feed Length", "26"));

// category "Smartwatches", name "Purchase Screen", and a property "sku"
Analytics.with(context).screen("Smartwatches", "Purchase Screen", new Properties().putValue("sku", "13d31"));
```

Kotlin

```kotlin
// category "Feed" and a property "Feed Length"
Analytics.with(context).screen("Feed", Properties().putValue("Feed Length", "26"))

// no category, name "Photo Feed" and a property "Feed Length"
Analytics.with(context).screen(null, "Photo Feed", Properties().putValue("Feed Length", "26"))

// category "Smartwatches", name "Purchase Screen", and a property "sku"
Analytics.with(context).screen("Smartwatches", "Purchase Screen", Properties().putValue("sku", "13d31"))
```

**Automatic Screen Recording**

```Java
Analytics analytics = new Analytics.Builder(context, writeKey)
  .recordScreenViews()
  .build();
```

### Group

Java

```java
Analytics.with(context).group("a user's id", "a group id", new Traits().putEmployees(20));
```

Kotlin

```kotlin
Analytics.with(context).group("a user's id", "a group id", Traits().putEmployees(20))
```

### Alias

Java and Kotlin

```java
Analytics.with(context).alias(newId);
Analytics.with(context).identify(newId);
```

### Context

Context is a dictionary of extra information you can provide about a specific API call. You can add any custom data to the context dictionary that you want to have access to in the raw logs.

```java
AnalyticsContext analyticsContext = Analytics.with(context).getAnalyticsContext();
analyticsContext.putValue(...).putReferrer(...).putCampaign(...);

// ...

AnalyticsContext analyticsContext = Analytics.with(context).getAnalyticsContext();
analyticsContext.device().putValue("advertisingId", "1");
```

## Consent Management

The SDK can stamp every event with CMP consent state (`context.consent.categoryPreferences`) and
gate destinations (including the Hightouch cloud destination) on consent categories via
`ConsentManager` and the `ConsentCategoryProvider` interface.

For OneTrust, use the `analytics-onetrust` artifact:

```gradle
dependencies {
  implementation 'com.hightouch.analytics.android:analytics-onetrust:X.Y.Z'
}
```

See [analytics-onetrust/README.md](analytics-onetrust/README.md) for full setup, and
`analytics-samples/kotlin-sample` for a working demo.
