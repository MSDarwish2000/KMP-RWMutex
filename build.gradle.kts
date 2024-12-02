plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false

    alias(libs.plugins.dokka)
    alias(libs.plugins.mavenPublish) apply false

    alias(libs.plugins.kotlinx.atomicfu)
    alias(libs.plugins.kotlinx.binaryCompatibilityValidator)
}

allprojects {
    repositories {
        mavenCentral()
    }

    afterEvaluate {
        tasks.withType<org.jetbrains.dokka.gradle.DokkaTaskPartial>().configureEach {
            dokkaSourceSets.configureEach {
                includes.from("module.md")
            }
        }

        tasks.withType<org.jetbrains.dokka.gradle.AbstractDokkaTask>().configureEach {
            // Workaround for Dokka configuration cache failure (issue: https://github.com/Kotlin/dokka/issues/1217)
            notCompatibleWithConfigurationCache("https://github.com/Kotlin/dokka/issues/1217")

            // When changing footerMessage, also update it in MkDocs config
            pluginsMapConfiguration.set(
                mapOf(
                    "org.jetbrains.dokka.base.DokkaBase" to """
                    {
                      "customStyleSheets": [
                        "${rootDir.toString().replace('\\', '/')}/docs/styles/dokka.css"
                      ],
                      "footerMessage": "Copyright &copy; 2024 MayakaApps."
                    }
                    """.trimIndent()
                )
            )
        }
    }

    // Workaround for yarn concurrency (issue: https://youtrack.jetbrains.com/issue/KT-43320)
    tasks.withType<org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask>().configureEach {
        args.addAll(listOf("--mutex", "file:${file("build/.yarn-mutex")}"))
    }

    // Workaround for Gradle implicit dependency error on publishing (issue: https://youtrack.jetbrains.com/issue/KT-46466)
    tasks.withType<AbstractPublishToMaven>().configureEach {
        dependsOn(tasks.withType<Sign>())
    }
}

tasks.withType<org.jetbrains.dokka.gradle.DokkaMultiModuleTask>().configureEach {
    moduleName.set("KMP-RWMutex")
    outputDirectory.set(file("$rootDir/docs/api"))
}
