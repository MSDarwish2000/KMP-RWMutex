<br />

<!--suppress HtmlDeprecatedAttribute -->
<h1 align="center" style="margin-top: 0;">KMP-RWMutex</h1>

<!--suppress HtmlDeprecatedAttribute -->
<div align="center">

![KMP-RWMutex](https://img.shields.io/badge/RWMutex-blue?logo=kotlin)
[![GitHub stars](https://img.shields.io/github/stars/MayakaApps/KMP-RWMutex)](https://github.com/MayakaApps/KMP-RWMutex/stargazers)
[![GitHub license](https://img.shields.io/github/license/MayakaApps/KMP-RWMutex)](https://github.com/MayakaApps/KMP-RWMutex/blob/main/LICENSE)
![Maven Central](https://img.shields.io/maven-central/v/com.mayakapps.rwmutex/rwmutex)
![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/com.mayakapps.rwmutex/rwmutex?server=https%3A%2F%2Fs01.oss.sonatype.org)

</div>

KMP-RWMutex is a lightweight Kotlin Multiplatform read-write mutex implementation based on Golang's `sync.RWMutex`.

## Setup (Gradle)

Kotlin DSL:

```kotlin
repositories {
    mavenCentral()

    // Add only if you're using snapshot version
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    implementation("com.mayakapps.rwmutex:rwmutex:<version>")
}
```

Groovy DSL:

```groovy
repositories {
    mavenCentral()

    // Add only if you're using snapshot version
    maven { url "https://s01.oss.sonatype.org/content/repositories/snapshots/" }
}

dependencies {
    implementation "com.mayakapps.rwmutex:rwmutex:<version>"
}
```

Don't forget to replace `<version>` with the latest version found on the badges above or the desired version.

## Usage

```kotlin
val rwMutex = ReadWriteMutex()

// You can use extension functions like `withReadLock` and `withWriteLock` for simpler usage.
rwMutex.withReadLock { /* read lock acquired */ }
rwMutex.withWriteLock { /* write lock acquired */ }

// Or you can use the mutexes directly.
val readMutex = rwMutex.readMutex
val writeMutex = rwMutex.writeMutex

// For state checks, you can use the `state` property which returns a snapshot of the current state.
val state = rwMutex.state
val isReadLocked = state.isReadLocked
```

For more information, please refer to the [API documentation](https://mayakaapps.github.io/KMP-RWMutex/api/rwmutex/com.mayakapps.rwmutex/-read-write-mutex/index.html).

## Documentation

See documentation [here](https://mayakaapps.github.io/KMP-RWMutex/latest/)

## License

All the code in this repository is licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for more
information.

## Contributing

All contributions are welcome. If you are reporting an issue, please use the provided template. If you're planning to
contribute to the code, please open an issue first describing what feature you're planning to add or what issue you're
planning to fix. This allows better discussion and coordination of efforts. You can also check open issues for
bugs/features that needs to be fixed/implemented.
