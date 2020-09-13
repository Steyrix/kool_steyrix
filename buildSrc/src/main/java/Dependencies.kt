import org.gradle.internal.os.OperatingSystem

object Versions {
    val kotlinVersion = "1.4.10"
    val kotlinCorroutinesVersion = "1.3.9"
    val kotlinSerializationVersion = "1.0.0-RC"

    val lwjglVersion = "3.2.3"
    val lwjglNatives = OperatingSystem.current().let {
        when {
            it.isLinux -> "natives-linux"
            it.isMacOsX -> "natives-macos"
            else -> "natives-windows"
        }
    }
}

object DepsCommon {
    val kotlinCoroutines = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinCorroutinesVersion}"
    val kotlinSerialization = "org.jetbrains.kotlinx:kotlinx-serialization-core:${Versions.kotlinSerializationVersion}"
}

object DepsJvm {
    val jTransforms = "com.github.wendykierp:JTransforms:3.1"

    fun lwjgl(subLib: String? = null): String {
        return if (subLib != null) {
            "org.lwjgl:lwjgl-$subLib:${Versions.lwjglVersion}"
        } else {
            "org.lwjgl:lwjgl:${Versions.lwjglVersion}"
        }
    }

    fun lwjglNatives(subLib: String? = null): String {
        return if (subLib != null) {
            "org.lwjgl:lwjgl-$subLib:${Versions.lwjglVersion}:${Versions.lwjglNatives}"
        } else {
            "org.lwjgl:lwjgl:${Versions.lwjglVersion}:${Versions.lwjglNatives}"
        }
    }
}

object DepsJs {

}