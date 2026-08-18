# Put the Samsung Health Data SDK AAR in this folder

1. Download the SDK from <https://developer.samsung.com/health/data/overview.html>
   (current release at the time of writing: **Samsung Health Data SDK v1.1.0**).
2. Unzip the archive.
3. Copy the AAR into **this exact folder**:

```
app/libs/samsung-health-data-api-1.1.0.aar
```

The file name does not have to match exactly - `app/build.gradle.kts` picks up
every `*.aar` in this directory:

```kotlin
implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
```

If no AAR is present, the build stops with an explicit message instead of a wall of
"unresolved reference" errors.
