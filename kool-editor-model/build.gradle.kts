@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import java.io.FileInputStream
import java.util.*

plugins {
//    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kotlinAtomicFu)
    alias(libs.plugins.kotlinDokka)

    `maven-publish`
    signing
}

kotlin {
//    androidTarget {
//        publishLibraryVariants("release", "debug")
//    }
    jvm("desktop") { }
    jvmToolchain(11)
    js(IR) {
        browser { }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
    }

    sourceSets {
        val desktopMain by getting
        commonMain.dependencies {
            api(project(":kool-core"))
            api(project(":kool-physics"))

            implementation(libs.kotlin.serialization.core)
            implementation(libs.kotlin.serialization.json)
            implementation(libs.kotlin.reflect)
            implementation(libs.kotlin.atomicfu)
        }
    }
}

//android {
//    namespace = "de.fabmax.kool"
//    compileSdk = libs.versions.android.compileSdk.get().toInt()
//
//    defaultConfig {
//        minSdk = libs.versions.android.minSdk.get().toInt()
//    }
//    compileOptions {
//        sourceCompatibility = JavaVersion.VERSION_11
//        targetCompatibility = JavaVersion.VERSION_11
//    }
//}

publishing {
    publications {
        publications.filterIsInstance<MavenPublication>().forEach { pub ->
            pub.pom {
                name.set("kool")
                description.set("A multiplatform OpenGL / Vulkan graphics engine written in kotlin")
                url.set("https://github.com/fabmax/kool")
                developers {
                    developer {
                        name.set("Max Thiele")
                        email.set("fabmax.thiele@gmail.com")
                        organization.set("github")
                        organizationUrl.set("https://github.com/fabmax")
                    }
                }
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/fabmax/kool.git")
                    developerConnection.set("scm:git:ssh://github.com:fabmax/kool.git")
                    url.set("https://github.com/fabmax/kool/tree/main")
                }
            }

            // generating javadoc isn't supported for multiplatform projects -> add a dummy javadoc jar
            // containing the README.md to make maven central happy
            var docJarAppendix = pub.name
            val docTaskName = "dummyJavadoc${pub.name}"
            if (pub.name == "kotlinMultiplatform") {
                docJarAppendix = ""
            }
            tasks.register<Jar>(docTaskName) {
                if (docJarAppendix.isNotEmpty()) {
                    archiveAppendix.set(docJarAppendix)
                }
                archiveClassifier.set("javadoc")
                from("$rootDir/README.md")
            }
            pub.artifact(tasks[docTaskName])
        }
    }

    if (File("publishingCredentials.properties").exists()) {
        val props = Properties()
        props.load(FileInputStream("publishingCredentials.properties"))

        repositories {
            maven {
                url = if (version.toString().endsWith("-SNAPSHOT")) {
                    uri("https://oss.sonatype.org/content/repositories/snapshots")
                } else {
                    uri("https://oss.sonatype.org/service/local/staging/deploy/maven2")
                }
                credentials {
                    username = props.getProperty("publishUser")
                    password = props.getProperty("publishPassword")
                }
            }
        }

        signing {
            publications.forEach {
                sign(it)
            }
        }
    }
}