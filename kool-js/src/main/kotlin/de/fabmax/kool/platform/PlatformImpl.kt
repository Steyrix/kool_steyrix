package de.fabmax.kool.platform

import de.fabmax.kool.KoolException
import de.fabmax.kool.Texture
import de.fabmax.kool.TextureData
import de.fabmax.kool.platform.js.*
import de.fabmax.kool.shading.ShaderProps
import de.fabmax.kool.util.CharMap
import de.fabmax.kool.util.Font
import de.fabmax.kool.util.GlslGenerator
import org.khronos.webgl.WebGLRenderingContext
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLImageElement
import kotlin.browser.document
import kotlin.js.Date

/**
 * Javascript / WebGL platform implementation
 *
 * @author fabmax
 */
class PlatformImpl private constructor() : Platform() {

    companion object {
        internal var jsContext: JsContext? = null
        internal val gl: WebGLRenderingContext
            get() = jsContext?.gl ?: throw KoolException("Platform.createContext() not called")
        internal val dpi: Float

        init {
            val measure = document.getElementById("dpiMeasure")
            if (measure == null) {
                println("dpiMeasure element not found, falling back to 96 dpi")
                println("Add this hidden div to your html:")
                println("<div id=\"dpiMeasure\" style=\"height: 1in; width: 1in; left: 100%; position: fixed; top: 100%;\"></div>")
                dpi = 96f
            } else {
                dpi = (measure as HTMLDivElement).offsetWidth.toFloat()
            }
        }

        fun init() {
            Platform.initPlatform(PlatformImpl())
        }

        val MAX_GENERATED_TEX_WIDTH = 1024
        val MAX_GENERATED_TEX_HEIGHT = 1024
    }

    internal val fontGenerator = FontMapGenerator(MAX_GENERATED_TEX_WIDTH, MAX_GENERATED_TEX_HEIGHT)

    override val supportsMultiContext = false

    override val supportsUint32Indices: Boolean
        get() = jsContext?.supportsUint32Indices ?: throw KoolException("Platform.createContext() not called")

    override fun createContext(props: RenderContext.InitProps): RenderContext{
        var ctx = jsContext
        if (ctx == null) {
            if (props is JsContext.InitProps) {
                ctx = JsContext(props)
                jsContext = ctx
            } else {
                throw IllegalArgumentException("Props must be of JsContext.InitProps")
            }
        }
        return ctx
    }

    override fun createDefaultShaderGenerator(): ShaderGenerator {
        val generator = GlslGenerator()
        generator.injectors += object: ShaderGenerator.GlslInjector {
            override fun fsStart(shaderProps: ShaderProps, text: StringBuilder) {
                text.append("precision highp float;")
            }
        }
        return generator
    }

    override fun getGlImpl(): GL.Impl {
        return WebGlImpl.instance
    }

    override fun getMathImpl(): Math.Impl {
        return JsMath()
    }

    override fun createUint8Buffer(capacity: Int): Uint8Buffer {
        return Uint8BufferImpl(capacity)
    }

    override fun createUint16Buffer(capacity: Int): Uint16Buffer {
        return Uint16BufferImpl(capacity)
    }

    override fun createUint32Buffer(capacity: Int): Uint32Buffer {
        return Uint32BufferImpl(capacity)
    }

    override fun createFloat32Buffer(capacity: Int): Float32Buffer {
        return Float32BufferImpl(capacity)
    }

    override fun currentTimeMillis(): Long {
        return Date().getTime().toLong()
    }

    override fun loadTextureAsset(assetPath: String): TextureData {
        val img = js("new Image();")
        val data = ImageTextureData(img)
        img.src = assetPath
        return data
    }

    override fun createCharMap(font: Font, chars: String): CharMap {
        return fontGenerator.createCharMap(font, chars)
    }

    private class JsMath : Math.Impl {
        override fun random() = kotlin.js.Math.random()
        override fun abs(value: Double) = kotlin.js.Math.abs(value)
        override fun acos(value: Double) = kotlin.js.Math.acos(value)
        override fun asin(value: Double) = kotlin.js.Math.asin(value)
        override fun atan(value: Double) = kotlin.js.Math.atan(value)
        override fun atan2(y: Double, x: Double) = kotlin.js.Math.atan2(x, y)
        override fun cos(value: Double) = kotlin.js.Math.cos(value)
        override fun sin(value: Double) = kotlin.js.Math.sin(value)
        override fun exp(value: Double) = kotlin.js.Math.exp(value)
        override fun max(a: Int, b: Int) = kotlin.js.Math.max(a, b)
        override fun max(a: Float, b: Float) = kotlin.js.Math.max(a, b)
        override fun max(a: Double, b: Double) = kotlin.js.Math.max(a, b)
        override fun min(a: Int, b: Int) = kotlin.js.Math.min(a, b)
        override fun min(a: Float, b: Float) = kotlin.js.Math.min(a, b)
        override fun min(a: Double, b: Double) = kotlin.js.Math.min(a, b)
        override fun sqrt(value: Double) = kotlin.js.Math.sqrt(value)
        override fun tan(value: Double) = kotlin.js.Math.tan(value)
        override fun log(value: Double) = kotlin.js.Math.log(value)
        override fun pow(base: Double, exp: Double) = kotlin.js.Math.pow(base, exp)
        override fun round(value: Double): Int = kotlin.js.Math.round(value)
        override fun round(value: Float): Int = kotlin.js.Math.round(value)
        override fun floor(value: Double): Int = kotlin.js.Math.floor(value)
        override fun floor(value: Float): Int = kotlin.js.Math.floor(value.toDouble())
        override fun ceil(value: Double): Int = kotlin.js.Math.ceil(value)
        override fun ceil(value: Float): Int = kotlin.js.Math.ceil(value.toDouble())
    }
}

class ImageTextureData(val image: HTMLImageElement) : TextureData() {
    override var isAvailable: Boolean
        get() = image.complete
        set(value) {}

    override fun onLoad(texture: Texture, ctx: RenderContext) {
        // fixme: is there a way to find out if the image has an alpha channel and set the GL format accordingly?
        PlatformImpl.gl.texImage2D(GL.TEXTURE_2D, 0, GL.RGBA, GL.RGBA, GL.UNSIGNED_BYTE, image)
        val size = image.width * image.height * 4
        ctx.memoryMgr.memoryAllocated(texture.res!!, size)
    }
    }
