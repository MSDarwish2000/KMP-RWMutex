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
    implementation("com.mayakapps.rwmutex:rwmutex:{{ versions.library }}")
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
    implementation "com.mayakapps.rwmutex:rwmutex:{{ versions.library }}"
}
```

## Usage

```kotlin
// To be implemented
```

## License

All the code in this repository is licensed under the Apache License, Version 2.0.

## Contributing

All contributions are welcome. If you are reporting an issue, please use the provided template. If you're planning to
contribute to the code, please open an issue first describing what feature you're planning to add or what issue you're
planning to fix. This allows better discussion and coordination of efforts. You can also check open issues for
bugs/features that needs to be fixed/implemented.
