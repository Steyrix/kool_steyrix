package de.fabmax.kool.pipeline.backend.gl

import de.fabmax.kool.math.MutableVec2i
import de.fabmax.kool.modules.ksl.KslUnlitShader
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.pipeline.FullscreenShaderUtil.fullscreenQuadVertexStage
import de.fabmax.kool.pipeline.FullscreenShaderUtil.generateFullscreenQuad
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.addTextureMesh

class SceneRenderPassGl(val numSamples: Int, backend: RenderBackendGl): GlRenderPass(backend) {
    private val renderFbo: GlFramebuffer by lazy { gl.createFramebuffer() }
    private val renderColor: GlRenderbuffer by lazy { gl.createRenderbuffer() }
    private val renderDepth: GlRenderbuffer by lazy { gl.createRenderbuffer() }

    private val resolveFbo: GlFramebuffer by lazy { gl.createFramebuffer() }
    private val resolvedColor = Texture2d(TextureProps(generateMipMaps = false, defaultSamplerSettings = SamplerSettings().clamped().nearest()))
    private val resolveDepth: GlRenderbuffer by lazy { gl.createRenderbuffer() }

    private val copyFbo: GlFramebuffer by lazy { gl.createFramebuffer() }

    private val renderSize = MutableVec2i()

    internal var resolveDirect = true

    private val blitScene: Scene by lazy {
        Scene().apply {
            addTextureMesh {
                generateFullscreenQuad()
                shader = KslUnlitShader {
                    pipeline { depthTest = DepthCompareOp.ALWAYS }
                    color { textureData(resolvedColor) }
                    modelCustomizer = { fullscreenQuadVertexStage(null) }
                }
            }
        }
    }

    override fun setupFramebuffer(viewIndex: Int, mipLevel: Int) {
        if (backend.useFloatDepthBuffer) {
            gl.bindFramebuffer(gl.FRAMEBUFFER, renderFbo)
        } else {
            gl.bindFramebuffer(gl.FRAMEBUFFER, gl.DEFAULT_FRAMEBUFFER)
        }
    }

    fun draw(scene: Scene) {
        val scenePass = scene.mainRenderPass
        renderViews(scenePass)
        scenePass.afterDraw()
    }

    override fun copy(frameCopy: FrameCopy) {
        val width = renderSize.x
        val height = renderSize.y
        frameCopy.setupCopyTargets(width, height, 1, gl.TEXTURE_2D)

        var blitMask = 0
        gl.bindFramebuffer(gl.FRAMEBUFFER, copyFbo)
        if (frameCopy.isCopyColor) {
            val loaded = frameCopy.colorCopy2d.gpuTexture as LoadedTextureGl
            gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, loaded.glTexture, 0)
            blitMask = gl.COLOR_BUFFER_BIT
        }
        if (frameCopy.isCopyDepth) {
            val loaded = frameCopy.depthCopy2d.gpuTexture as LoadedTextureGl
            gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.DEPTH_ATTACHMENT, gl.TEXTURE_2D, loaded.glTexture, 0)
            blitMask = blitMask or gl.DEPTH_BUFFER_BIT
        }

        resolve(copyFbo, blitMask)
    }

    fun resolve(targetFbo: GlFramebuffer, blitMask: Int) {
        if (resolveDirect || targetFbo != gl.DEFAULT_FRAMEBUFFER) {
            blitFramebuffers(renderFbo, targetFbo, blitMask)
        } else {
            // on WebGL trying to resolve a multi-sampled framebuffer into the default framebuffer fails with
            // "GL_INVALID_OPERATION: Invalid operation on multi-sampled framebuffer". As a work-around we resolve
            // the multi-sampled framebuffer into a non-multi-sampled one, which is then rendered to the default
            // framebuffer (i.e. screen) using a copy shader.
            blitFramebuffers(renderFbo, resolveFbo, blitMask)

            gl.bindFramebuffer(gl.FRAMEBUFFER, targetFbo)
            blitScene.mainRenderPass.update(backend.ctx)
            blitScene.mainRenderPass.collectDrawCommands(backend.ctx)
            renderView(blitScene.mainRenderPass.screenView, 0, 0)
        }
    }

    private fun blitFramebuffers(src: GlFramebuffer, dst: GlFramebuffer, blitMask: Int) {
        gl.bindFramebuffer(gl.READ_FRAMEBUFFER, src)
        gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER, dst)
        gl.blitFramebuffer(
            0, 0, renderSize.x, renderSize.y,
            0, 0, renderSize.x, renderSize.y,
            blitMask, gl.NEAREST
        )
    }

    private fun bindTargetTex(dst: Texture2d, width: Int, height: Int, isColor: Boolean) {
        if (dst.gpuTexture == null) {
            dst.gpuTexture = LoadedTextureGl(
                target = gl.TEXTURE_2D,
                glTexture = gl.createTexture(),
                backend = backend,
                texture = dst,
                estimatedSize = width * height * 4L
            )
        }
        val tex = dst.gpuTexture as LoadedTextureGl
        tex.bind()

        if (tex.width != width || tex.height != height) {
            tex.setSize(width, height, 1)
            tex.applySamplerSettings(dst.props.defaultSamplerSettings)
            dst.loadingState = Texture.LoadingState.LOADED

            val internalFormat = if (isColor) gl.RGBA8 else gl.DEPTH_COMPONENT32F
            val format = if (isColor) gl.RGBA else gl.DEPTH_COMPONENT
            val type = if (isColor) gl.UNSIGNED_BYTE else gl.FLOAT
            gl.texImage2d(tex.target, 0, internalFormat, width, height, 0,  format, type, null)
        }
    }

    fun applySize(width: Int, height: Int) {
        if (width == renderSize.x && height == renderSize.y) {
            return
        }
        renderSize.set(width, height)

        val colorFormat = TexFormat.RGBA.glInternalFormat(gl)
        val depthFormat = gl.DEPTH_COMPONENT32F
        val isMultiSampled = numSamples > 1

        gl.bindRenderbuffer(gl.RENDERBUFFER, renderColor)
        if (isMultiSampled) {
            gl.renderbufferStorageMultisample(gl.RENDERBUFFER, backend.numSamples, colorFormat, width, height)
        } else {
            gl.renderbufferStorage(gl.RENDERBUFFER, colorFormat, width, height)
        }

        gl.bindRenderbuffer(gl.RENDERBUFFER, renderDepth)
        if (isMultiSampled) {
            gl.renderbufferStorageMultisample(gl.RENDERBUFFER, backend.numSamples, depthFormat, width, height)
        } else {
            gl.renderbufferStorage(gl.RENDERBUFFER, depthFormat, width, height)
        }

        gl.bindFramebuffer(gl.FRAMEBUFFER, renderFbo)
        gl.framebufferRenderbuffer(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.RENDERBUFFER, renderColor)
        gl.framebufferRenderbuffer(gl.FRAMEBUFFER, gl.DEPTH_ATTACHMENT, gl.RENDERBUFFER, renderDepth)

        if (!resolveDirect) {
            makeResolveFbo(width, height)
        }
    }

    private fun makeResolveFbo(width: Int, height: Int) {
        var loadedTex = resolvedColor.gpuTexture as LoadedTextureGl?
        loadedTex?.release()

        val estSize = Texture.estimatedTexSize(renderSize.x, renderSize.y, 1, 1, 4).toLong()
        loadedTex = LoadedTextureGl(gl.TEXTURE_2D, gl.createTexture(), backend, resolvedColor, estSize)
        resolvedColor.gpuTexture = loadedTex
        resolvedColor.loadingState = Texture.LoadingState.LOADED

        loadedTex.setSize(width, height, 1)
        loadedTex.bind()
        gl.texStorage2d(gl.TEXTURE_2D, 1, TexFormat.RGBA.glInternalFormat(gl), renderSize.x, renderSize.y)
        loadedTex.applySamplerSettings(resolvedColor.props.defaultSamplerSettings)

        gl.bindRenderbuffer(gl.RENDERBUFFER, resolveDepth)
        gl.renderbufferStorage(gl.RENDERBUFFER, gl.DEPTH_COMPONENT32F, width, height)

        gl.bindFramebuffer(gl.FRAMEBUFFER, resolveFbo)
        gl.framebufferRenderbuffer(gl.FRAMEBUFFER, gl.DEPTH_ATTACHMENT, gl.RENDERBUFFER, resolveDepth)
        gl.framebufferTexture2D(
            target = gl.FRAMEBUFFER,
            attachment = gl.COLOR_ATTACHMENT0,
            textarget = gl.TEXTURE_2D,
            texture = (resolvedColor.gpuTexture as LoadedTextureGl).glTexture,
            level = 0
        )
        gl.drawBuffers(intArrayOf(gl.COLOR_ATTACHMENT0))
    }
}