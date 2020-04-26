package de.fabmax.kool.platform.gl

import de.fabmax.kool.KoolContext
import de.fabmax.kool.pipeline.OffscreenPassCubeImpl
import de.fabmax.kool.pipeline.OffscreenRenderPassCube
import de.fabmax.kool.pipeline.Texture
import de.fabmax.kool.platform.Lwjgl3Context
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL42.glTexStorage2D

class OffscreenPassCubeGl(val parentPass: OffscreenPassCubeImpl) : OffscreenPassCubeImpl.BackendImpl {

    private var fbos: List<Int> = mutableListOf()
    private var rbos: List<Int> = mutableListOf()

    private var isCreated = false

    private var glColorTex = 0

    override fun draw(ctx: Lwjgl3Context) {
        if (!isCreated) {
            create(ctx)
            isCreated = true
        }

        val mipLevel = parentPass.offscreenPass.targetMipLevel
        val width = parentPass.offscreenPass.mipWidth(mipLevel)
        val height = parentPass.offscreenPass.mipHeight(mipLevel)
        val clearColor = parentPass.offscreenPass.clearColor
        val fboIdx = if (mipLevel < 0) 0 else mipLevel

        parentPass.offscreenPass.viewport = KoolContext.Viewport(0, 0, width, height)
        glBindFramebuffer(GL_FRAMEBUFFER, fbos[fboIdx])

        for (i in 0 until 6) {
            val view = VIEWS[i]
            val queue = parentPass.offscreenPass.drawQueues[view.index]
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, glColorTex, fboIdx)
            val glBackend = ctx.renderBackend as GlRenderBackend
            glBackend.queueRenderer.renderQueue(queue)
        }

        glBindFramebuffer(GL_FRAMEBUFFER, GL11.GL_NONE)
    }

    override fun dispose(ctx: Lwjgl3Context) {
        fbos.forEach { glDeleteFramebuffers(it) }
        rbos.forEach { glDeleteRenderbuffers(it) }
        parentPass.texture.dispose()
    }

    private fun create(ctx: Lwjgl3Context) {
        createColorTex(ctx)

        for (i in 0 until parentPass.offscreenPass.mipLevels) {
            val fbo = glGenFramebuffers()
            val rbo = glGenRenderbuffers()

            glBindFramebuffer(GL_FRAMEBUFFER, fbo)
            glBindRenderbuffer(GL_RENDERBUFFER, rbo)
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, parentPass.offscreenPass.texWidth shr i, parentPass.offscreenPass.texHeight shr i)
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, rbo)

            fbos += fbo
            rbos += rbo
        }
    }

    private fun createColorTex(ctx: Lwjgl3Context) {
        val intFormat = parentPass.offscreenPass.colorFormat.glInternalFormat
        val width = parentPass.offscreenPass.texWidth
        val height = parentPass.offscreenPass.texHeight

        glColorTex = glGenTextures()
        glBindTexture(GL_TEXTURE_CUBE_MAP, glColorTex)
        glTexStorage2D(GL_TEXTURE_CUBE_MAP, parentPass.offscreenPass.mipLevels, intFormat, width, height)

        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR)

        val estSize = Texture.estimatedTexSize(width, height, parentPass.offscreenPass.colorFormat.pxSize, 6, parentPass.offscreenPass.mipLevels)
        parentPass.texture.loadedTexture = LoadedTextureGl(ctx, glColorTex, estSize)
        parentPass.texture.loadingState = Texture.LoadingState.LOADED
    }

    companion object {
        private val VIEWS = Array(6) { i ->
            when (i) {
                0 -> OffscreenRenderPassCube.ViewDirection.RIGHT
                1 -> OffscreenRenderPassCube.ViewDirection.LEFT
                2 -> OffscreenRenderPassCube.ViewDirection.UP
                3 -> OffscreenRenderPassCube.ViewDirection.DOWN
                4 -> OffscreenRenderPassCube.ViewDirection.FRONT
                5 -> OffscreenRenderPassCube.ViewDirection.BACK
                else -> throw IllegalStateException()
            }
        }
    }
}