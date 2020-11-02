plugins {
    kotlin("multiplatform") version Versions.kotlinVersion apply false
}

allprojects {
    group = "de.fabmax.kool"
    version = "0.6.0"

    repositories {
        jcenter()
        mavenCentral()
    }

    subprojects {
        apply(plugin = "kotlin-multiplatform")
    }
}
