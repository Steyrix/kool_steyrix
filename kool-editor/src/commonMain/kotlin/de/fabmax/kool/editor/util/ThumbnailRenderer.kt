package de.fabmax.kool.editor.util

import de.fabmax.kool.editor.KoolEditor
import de.fabmax.kool.editor.api.AssetReference
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.blocks.ColorSpaceConversion
import de.fabmax.kool.modules.ksl.blocks.ToneMapping
import de.fabmax.kool.modules.ksl.blocks.cameraData
import de.fabmax.kool.modules.ksl.blocks.convertColorSpace
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.modules.ui2.ImageProvider
import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.pipeline.FullscreenShaderUtil.fullscreenQuadVertexStage
import de.fabmax.kool.pipeline.FullscreenShaderUtil.generateFullscreenQuad
import de.fabmax.kool.scene.Mesh
import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.OrthographicCamera
import de.fabmax.kool.scene.TextureMesh
import de.fabmax.kool.util.MdColor
import de.fabmax.kool.util.Time
import de.fabmax.kool.util.launchOnMainThread
import de.fabmax.kool.util.releaseWith
import kotlin.collections.set
import kotlin.math.min

class ThumbnailRenderer(
    name: String,
    val numTiles: Vec2i = Vec2i(24, 24),
    tileSize: Vec2i = Vec2i(80 ,80)
) : OffscreenRenderPass2d(
    drawNode = Node(),
    attachmentConfig = colorAttachmentNoDepth(TexFormat.RGBA),
    initialSize = numTiles * tileSize,
    name = name
) {

    var tileSize: Vec2i = tileSize
        private set

    private val freeIndices = mutableListOf<Vec2i>()
    private val thumbnails = mutableMapOf<Vec2i, Thumbnail>()
    private val renderQueue = mutableListOf<Pair<View, Thumbnail>>()

    init {
        views.clear()
        clearColor = null

        for (y in 0 until numTiles.y) {
            for (x in 0 until numTiles.x) {
                freeIndices += Vec2i(x, y)
            }
        }

        onAfterDraw {
            renderQueue.forEach { (view, thumbnail) ->
                view.drawNode.release()
                thumbnail.isLoaded.set(true)
                thumbnails[thumbnail.tileIndex] = thumbnail
            }
            renderQueue.clear()
            views.clear()
            isEnabled = false
        }
    }

    fun updateTileSize(sz: Int) {
        if (sz != tileSize.x || sz != tileSize.y) {
            tileSize = Vec2i(sz, sz)
            setSize(numTiles.x * sz, numTiles.y * sz)
            thumbnails.values.forEach {
                it.isReleased.set(true)
            }
            thumbnails.clear()
        }
    }

    private fun enqueueView(view: View, thumbnail: Thumbnail) {
        renderQueue += view to thumbnail
        views += view
        isEnabled = true
    }

    fun renderThumbnail(content: suspend () -> Node): Thumbnail {
        val result = Thumbnail(getNextTileIndex())
        launchOnMainThread {
            val node = content()
            val cam = OrthographicCamera().apply {
                left = -1f
                right = 1f
                top = 1f
                bottom = -1f
            }
            val view = View("thumbnail-view", node, cam).apply {
                val vpX = result.tileIndex.x * tileSize.x
                val vpY = result.tileIndex.y * tileSize.y
                viewport.set(vpX, vpY, tileSize.x, tileSize.y)
            }
            enqueueView(view, result)
        }
        return result
    }

    private fun getNextTileIndex(): Vec2i {
        return if (freeIndices.isNotEmpty()) {
            freeIndices.removeLast()
        } else {
            val lru = thumbnails.values.minBy { it.lastUsed }.tileIndex
            val old = thumbnails.remove(lru)
            old?.isReleased?.set(true)
            lru
        }
    }

    inner class Thumbnail(val tileIndex: Vec2i) : ImageProvider {
        override val uvTopLeft: Vec2f
        override val uvTopRight: Vec2f
        override val uvBottomLeft: Vec2f
        override val uvBottomRight: Vec2f
        override val isDynamicSize = false

        val isLoaded = mutableStateOf(false)
        val isReleased = mutableStateOf(false)
        var lastUsed = Time.frameCount

        init {
            val lt = tileIndex.x.toFloat() / numTiles.x
            val rt = (tileIndex.x + 1).toFloat() / numTiles.x
            val top = 1f - tileIndex.y.toFloat() / numTiles.y
            val bot = 1f - (tileIndex.y + 1).toFloat() / numTiles.y
            uvTopLeft = Vec2f(lt, top)
            uvTopRight = Vec2f(rt, top)
            uvBottomLeft = Vec2f(lt, bot)
            uvBottomRight = Vec2f(rt, bot)
        }

        override fun getTexture(imgWidthPx: Float, imgHeightPx: Float): Texture2d? = colorTexture
    }
}

fun ThumbnailRenderer.textureThumbnail(texPath: String): ThumbnailRenderer.Thumbnail {
    return renderThumbnail {
        val texMesh = TextureMesh().apply {
            val assets = KoolEditor.instance.cachedAppAssets
            val ref = AssetReference.Texture(texPath)
            var tex = assets.getTextureIfLoaded(ref)
            if (tex == null) {
                tex = assets.assetLoader.loadTexture2d(texPath)
                tex.releaseWith(this)
            }

            generate {
                rect {
                    val ar = tex.width.toFloat() / tex.height
                    if (ar > 1f) size.set(2f, 2f / ar) else size.set(2f * ar, 2f)
                    cornerRadius = min(size.x, size.y) * 0.1f
                }
            }

            shader = KslShader("tex2d-thumbnail-shader") {
                val uv = interStageFloat2()
                vertexStage {
                    main {
                        val pos = vertexAttribFloat3(Attribute.POSITIONS)
                        uv.input set vertexAttribFloat2(Attribute.TEXTURE_COORDS)
                        outPosition set float4Value(pos, 1f.const) * cameraData().viewProjMat
                    }
                }
                fragmentStage {
                    main {
                        val p = float2Var(fract(uv.output * cameraData().viewport.zw / 20f.const))
                        val a = bool1Var((p.x lt 0.5f.const) eq (p.y lt 0.5f.const))
                        val bg = float4Var()
                        `if`(a) {
                            bg set (MdColor.GREY tone 200).const
                        }.`else` {
                            bg set (MdColor.GREY tone 350).const
                        }
                        val color = sampleTexture(texture2d("thumb"), uv.output)
                        bg.rgb set mix(bg.rgb, color.rgb, color.a)
                        colorOutput(bg)
                    }
                }
            }.also {
                it.texture2d("thumb", tex)
            }
        }

        Node().apply {
            addNode(ClearMesh())
            addNode(texMesh)
        }
    }
}

fun ThumbnailRenderer.hdriThumbnail(texPath: String): ThumbnailRenderer.Thumbnail {
    return renderThumbnail {
        val texMesh = TextureMesh().apply {
            val assets = KoolEditor.instance.cachedAppAssets
            val tex = assets.assetLoader.loadTexture2d(texPath, TextureProps(generateMipMaps = false))
            tex.releaseWith(this)

            generate {
                rect {
                    val ar = tex.width.toFloat() / tex.height
                    if (ar > 1f) size.set(2f, 2f / ar) else size.set(2f * ar, 2f)
                    cornerRadius = min(size.x, size.y) * 0.1f
                }
            }

            shader = KslShader("hdri-thumbnail-shader") {
                val uv = interStageFloat2()
                vertexStage {
                    main {
                        val pos = vertexAttribFloat3(Attribute.POSITIONS)
                        uv.input set vertexAttribFloat2(Attribute.TEXTURE_COORDS)
                        outPosition set float4Value(pos, 1f.const) * cameraData().viewProjMat
                    }
                }
                fragmentStage {
                    main {
                        val rgbe = float4Var(sampleTexture(texture2d("thumb"), uv.output))
                        val exp = float1Var(rgbe.w * 255f.const - 128f.const)
                        val rgb = float3Var(rgbe.rgb * pow(2f.const, exp))
                        colorOutput(convertColorSpace(rgb, ColorSpaceConversion.LinearToSrgbHdr(ToneMapping.Aces)))
                    }
                }
            }.also {
                it.texture2d("thumb", tex)
            }
        }

        Node().apply {
            addNode(ClearMesh())
            addNode(texMesh)
        }
    }
}

class ClearMesh : Mesh(Attribute.POSITIONS, Attribute.TEXTURE_COORDS) {
    init {
        generateFullscreenQuad()
        shader = KslShader("clear-shader", PipelineConfig(blendMode = BlendMode.DISABLED, depthTest = DepthCompareOp.ALWAYS)) {
            val uv = interStageFloat2()
            vertexStage { fullscreenQuadVertexStage(uv) }
            fragmentStage {
                main {
                    colorOutput(float4Value(0f, 0f, 0f, 0f))
                }
            }
        }
    }
}