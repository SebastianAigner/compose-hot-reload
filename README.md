# 🔥 Compose Hot Reload

[![JetBrains team project](https://jb.gg/badges/incubator.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="./readme-assets/banner_dark.png">
  <img alt="Text changing depending on mode. Light: 'So light!' Dark: 'So dark!'" src="./readme-assets/banner_light.png">
</picture>

Iterate on your compose UIs faster, and let your creativity run free when building multiplatform user interfaces.

**Compose Hot Reload is currently experimental.** No guarantees apply.

## Getting Started

### Requirements
- A Kotlin Multiplatform project with a desktop target (see [FAQ](#faq))
- Kotlin `2.1.20-Beta2` or higher 

### Add repository
The project publishes experimental builds. To obtain the Compose Hot Reload artifacts, first add the `firework` maven repository:

In your projects' `settings.gradle.kts`, add the following: 

```kotlin
pluginManagement {
    repositories {
        maven("https://packages.jetbrains.team/maven/p/firework/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://packages.jetbrains.team/maven/p/firework/dev")
    }
}

```

### Apply the Gradle plugin to your project

Add the `org.jetbrains.compose.hot-reload` Gradle plugin to your build script:

```kotlin
plugins {
    kotlin("multiplatform") version "2.1.20-Beta2" // <- Use Kotlin 2.1.20-Beta2 or higher!
    kotlin("plugin.compose") version "2.1.20-Beta2" // <- Use Compose Compiler Plugin 2.1.20-Beta2 or higher!
    id("org.jetbrains.compose")
    id("org.jetbrains.compose.hot-reload") version "1.0.0-dev-55" // <- add this additionally
}
```

### Enable 'OptimizeNonSkippingGroups':

Add the following to your `build.gradle.kts`:

```kotlin
composeCompiler {
    featureFlags.add(ComposeFeatureFlag.OptimizeNonSkippingGroups)
}
```

#### Setup automatic download of the JetBrains Runtime (JBR) via Gradle (`foojay-resolver-convention`)

https://github.com/gradle/foojay-toolchains

```kotlin
// settings.gradle.kts
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
```


### Optional: Create a custom entry point to launch your hot application

```kotlin
// build.gradle.kts
tasks.register<ComposeHotRun>("runHot") {
    mainClass.set("my.app.MainKt")
}
```

### Provide an entry point for your UI to hot-reload

In your `desktop` source set, add the following code 

```kotlin
fun main() {
    singleWindowApplication(
        title = "My CHR App",
        state = WindowState(width = 800.dp, height = 800.dp),
        alwaysOnTop = true
    ) {
        DevelopmentEntryPoint {
            MainPage()
        }
    }
}

@Composable
fun MainPage() {
    Text("🔥") // Write your own code, call your own composables, or load an entire app
}
```

## FAQ

### I am developing an Android application and am not using Kotlin Multiplatform. Can I use Compose Hot Reload?


### My project is using Kotlin Multiplatform but doesn't have a Desktop target. Can I  use Compose Hot Reload?


### My project is a desktop-only app with Compose Multiplatform. Can I use Compose Hot Reload?

Yes! However, please note that you can't start the application via the run button in the gutter ([CMP-3123](https://youtrack.jetbrains.com/issue/CMP-3123)). Instead, use the custom Gradle task as described above.