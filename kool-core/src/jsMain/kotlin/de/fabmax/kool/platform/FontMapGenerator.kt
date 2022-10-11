package de.fabmax.kool.platform

import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.clamp
import de.fabmax.kool.math.smoothStep
import de.fabmax.kool.pipeline.TexFormat
import de.fabmax.kool.pipeline.TextureData2d
import de.fabmax.kool.util.*
import kotlinx.browser.document
import kotlinx.browser.window
import org.khronos.webgl.get
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import kotlin.js.Promise
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * @author fabmax
 */

class FontMapGenerator(val maxWidth: Int, val maxHeight: Int, props: JsContext.InitProps, assetManager: JsAssetManager, val ctx: KoolContext) {

    private val canvas = document.createElement("canvas") as HTMLCanvasElement
    private val canvasCtx: CanvasRenderingContext2D

    val loadingFonts = mutableListOf<Promise<FontFace>>()

    init {
        canvas.style.width = "${(maxWidth / window.devicePixelRatio).roundToInt()}"
        canvas.style.height = "${(maxHeight / window.devicePixelRatio).roundToInt()}"
        canvas.width = maxWidth
        canvas.height = maxHeight
        canvasCtx = canvas.getContext("2d") as CanvasRenderingContext2D

        props.customFonts.forEach { (family, url) ->
            loadFont(family, assetManager.makeAssetRef(url).url)
        }
    }

    private fun loadFont(family: String, url: String) {
        val font = FontFace(family, "url($url)")
        val promise = font.load()
        loadingFonts += promise
        promise.then { f ->
            js("document.fonts.add(f);")
            logD { "Loaded custom font: ${f.family}" }
        }
    }

    fun createFontMapData(font: Font, fontScale: Float, outMetrics: MutableMap<Char, CharMetrics>): TextureData2d {
        val fontSize = (font.sizePts * fontScale * font.sampleScale).roundToInt()

        // clear canvas
        canvasCtx.fillStyle = "#000000"
        canvasCtx.fillRect(0.0, 0.0, maxWidth.toDouble(), maxHeight.toDouble())
        // draw font chars
        val texHeight = makeMap(font, fontSize, outMetrics)
        // copy image data
        val data = canvasCtx.getImageData(0.0, 0.0, maxWidth.toDouble(), texHeight.toDouble())

        // alpha correction lut:
        // boost font contrast by increasing contrast / reducing anti-aliasing (otherwise small fonts appear quite
        // blurry, especially in Chrome)
        val alphaLut = ByteArray(256) { i ->
            val a = i / 255f
            // corrected value: boosted contrast
            val ac = a.pow(1.5f) * 1.3f - 0.15f
            // mix original value and corrected one based on font size:
            // max correction for sizes <= 12, no correction for sizes >= 36
            val cw = smoothStep(12f, 36f, fontSize.toFloat())
            val c = a * cw + ac * (1f - cw)
            (c.clamp(0f, 1f) * 255f).toInt().toByte()
        }

        // alpha texture
        val buffer = createUint8Buffer(maxWidth * texHeight)
        for (i in 0 until buffer.capacity) {
            val a = data.data[i*4].toInt() and 0xff
            buffer.put(alphaLut[a])
        }
        logD { "Generated font map for (${font.toStringShort()}, scale=${fontScale}x${font.sampleScale})" }
        return TextureData2d(buffer, maxWidth, texHeight, TexFormat.R)
    }

    private fun makeMap(fontProps: Font, size: Int, outMetrics: MutableMap<Char, CharMetrics>): Int {
        var style = ""
        if (fontProps.style and Font.BOLD != 0) {
            style = "bold "
        }
        if (fontProps.style and Font.ITALIC != 0) {
            style += "italic "
        }

        val fontStr = "$style ${size}px ${fontProps.family}"
        canvasCtx.font = fontStr
        canvasCtx.fillStyle = "#ffffff"
        canvasCtx.strokeStyle = "#ffffff"

        logD { "generate font: $fontStr" }

        val fm = canvasCtx.measureText("A")

        val padLeft = ceil(size / 10f).toInt()
        val padRight = ceil(size / 10f).toInt()
        val padTop = 0
        val padBottom = ceil(size / 10f).toInt()

        // firefox currently does not have font ascent / descent measures
        val ascent = if (fm.fontBoundingBoxAscent === undefined) {
            ceil(size * 1.1f).toInt()
        } else {
            ceil(fm.fontBoundingBoxAscent).toInt()
        }
        val descent = if (fm.fontBoundingBoxAscent === undefined) {
            ceil(size * 0.25f).toInt()
        } else {
            ceil(fm.fontBoundingBoxAscent).toInt()
        }
        // overall line height - unfortunately js does not offer a font-specific measure, we just take a guess...
        val height = (size * 1.35).roundToInt()

        // first pixel is opaque
        canvasCtx.beginPath()
        canvasCtx.moveTo(0.5, 0.0)
        canvasCtx.lineTo(0.5, 1.0)
        canvasCtx.stroke()

        var x = 1
        var y = ascent
        for (c in fontProps.chars) {
            val txt = "$c"
            val txtMetrics = canvasCtx.measureText(txt)
            val charW = ceil(txtMetrics.actualBoundingBoxRight + txtMetrics.actualBoundingBoxLeft).toInt()
            val paddedWidth = charW + padLeft + padRight
            if (x + paddedWidth > maxWidth) {
                x = 0
                y += (height + padBottom + padTop)
                if (y + descent > maxHeight) {
                    logE { "Unable to render full font map: Maximum texture size exceeded" }
                    break
                }
            }

            val xOff = txtMetrics.actualBoundingBoxLeft + padLeft
            val metrics = CharMetrics()
            metrics.width = paddedWidth / fontProps.sampleScale
            metrics.height = (height + padBottom + padTop) / fontProps.sampleScale
            metrics.xOffset = xOff.toFloat() / fontProps.sampleScale
            metrics.yBaseline = ascent / fontProps.sampleScale
            metrics.advance = txtMetrics.width.toFloat() / fontProps.sampleScale

            metrics.uvMin.set(
                x.toFloat(),
                (y - ascent - padTop).toFloat()
            )
            metrics.uvMax.set(
                (x + paddedWidth).toFloat(),
                (y - ascent + padBottom + height).toFloat()
            )
            outMetrics[c] = metrics

            canvasCtx.fillText(txt, x + xOff, y.toDouble())
            x += paddedWidth
        }

        val texW = maxWidth
        val texH = nextPow2(y + descent)

        for (cm in outMetrics.values) {
            cm.uvMin.x /= texW
            cm.uvMin.y /= texH
            cm.uvMax.x /= texW
            cm.uvMax.y /= texH
        }

        return texH
    }

    private fun nextPow2(value: Int): Int {
        var pow2 = 16
        while (pow2 < value && pow2 < maxHeight) {
            pow2 = pow2 shl 1
        }
        return pow2
    }
}

external class FontFace(family: String, source: String) {
    val family: String

    fun load(): Promise<FontFace>
}
