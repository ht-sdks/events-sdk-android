# Events SDK Android

## Installing the SDK

This SDK is available through [**JitPack**](https://jitpack.io/#ht-sdks/events-sdk-android/).

1. Add JitPack to your build

```gradle
  allprojects {
    repositories {
      ...
      maven { url 'https://jitpack.io' }
    }
  }
```

2. Add your dependendcy

```gradle
  dependencies {
    implementation 'com.github.ht-sdks.events-sdk-android:analytics:0.0.5'
  }
```

See the project's [JitPack page](https://jitpack.io/#ht-sdks/events-sdk-android/) for available build tags. Be sure to update the semver numbers after `:analytics:` (e.g.`:analytics:$.$.$`) to your desired build.

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
