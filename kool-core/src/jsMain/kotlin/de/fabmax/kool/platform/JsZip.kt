package de.fabmax.kool.platform

import org.khronos.webgl.Uint8Array
import org.w3c.files.Blob
import kotlin.js.Promise

@JsModule("jszip")
@JsNonModule
external class JsZip {
    fun file(name: String, data: Uint8Array): JsZip
    fun folder(name: String): JsZip
    fun forEach(callback: (relativePath: String, file: ZipObject) -> Unit)
    fun loadAsync(data: Blob): Promise<JsZip>
    fun loadAsync(data: Uint8Array): Promise<JsZip>
}

@JsModule("jszip")
@JsNonModule
external interface ZipObject {
    val name: String
    val dir: Boolean

    fun async(type: String): Promise<dynamic>
}

fun ZipObject.asyncU8(): Promise<Uint8Array> {
    return async("uint8array")
}

fun ZipObject.asyncText(): Promise<String> {
    return async("text")
}
