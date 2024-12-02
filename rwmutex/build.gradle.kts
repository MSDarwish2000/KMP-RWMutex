import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)

    alias(libs.plugins.dokka)
    alias(libs.plugins.mavenPublish)
}

group = "com.mayakapps.rwmutex"
version = "1.0.0-SNAPSHOT"

kotlin {
    explicitApi()

    jvm {
        compilerOptions.jvmTarget = JvmTarget.JVM_1_8
    }

    js {
        browser()
        nodejs()
    }

    @Suppress("OPT_IN_USAGE") wasmJs {
        browser()
        nodejs()
    }

    @Suppress("OPT_IN_USAGE") wasmWasi {
        nodejs()
    }

    macosX64()
    macosArm64()

    iosArm64()
    iosX64()
    iosSimulatorArm64()

    watchosArm32()
    watchosArm64()
    watchosX64()
    watchosDeviceArm64()
    watchosSimulatorArm64()

    tvosArm64()
    tvosX64()
    tvosSimulatorArm64()

    linuxX64()
    linuxArm64()

    mingwX64()

    androidNativeArm32()
    androidNativeArm64()
    androidNativeX86()
    androidNativeX64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.atomicfu)
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))

                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val jsAndWasmTest by creating { dependsOn(commonTest) }
        jsTest { dependsOn(jsAndWasmTest) }
        wasmJsTest { dependsOn(jsAndWasmTest) }
        wasmWasiTest { dependsOn(jsAndWasmTest) }
    }
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    signAllPublications()

    coordinates(group.toString(), "library", version.toString())

    pom {
        name = "KMP RWMutex"
        description = "A read-write mutex for Kotlin Multiplatform based on Golang's sync.RWMutex implementation."
        inceptionYear = "2024"
        url = "https://github.com/MayakaApps/KMP-RWMutex/"

        licenses {
            license {
                name = "Apache License, Version 2.0"
                url = "https://raw.githubusercontent.com/MayakaApps/KMP-RWMutex/main/LICENSE"
                distribution = "repo"
            }
        }

        developers {
            developer {
                id = "MayakaApps"
                name = "MayakaApps"
                url = "https://github.com/MayakaApps/"
            }
        }

        scm {
            url = "https://github.com/MayakaApps/KMP-RWMutex.git"
            connection = "scm:git:git://github.com/MayakaApps/KMP-RWMutex.git"
            developerConnection = "scm:git:ssh://git@github.com/MayakaApps/KMP-RWMutex.git"
        }
    }
}
