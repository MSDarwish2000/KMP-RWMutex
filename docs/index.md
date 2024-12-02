KMP-RWMutex is a lightweight Kotlin Multiplatform read-write mutex implementation based on Golang's `sync.RWMutex`.

## Setup (Gradle)

Kotlin DSL:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("com.mayakapps.rwmutex:rwmutex:{{ versions.library }}")
}
```

Groovy DSL:

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation "com.mayakapps.rwmutex:rwmutex:{{ versions.library }}"
}
```

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

For more information, please refer to the [API documentation](api/rwmutex/com.mayakapps.rwmutex/-read-write-mutex/index.html).

## License

All the code in this repository is licensed under the Apache License, Version 2.0.

## Contributing

All contributions are welcome. If you are reporting an issue, please use the provided template. If you're planning to
contribute to the code, please open an issue first describing what feature you're planning to add or what issue you're
planning to fix. This allows better discussion and coordination of efforts. You can also check open issues for
bugs/features that needs to be fixed/implemented.
