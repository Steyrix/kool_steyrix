package de.fabmax.kool.pipeline.backend.webgpu

import de.fabmax.kool.KoolContext
import de.fabmax.kool.pipeline.OffscreenPass2dImpl
import de.fabmax.kool.pipeline.OffscreenRenderPass2d
import de.fabmax.kool.pipeline.Texture
import de.fabmax.kool.util.BaseReleasable
import de.fabmax.kool.util.releaseWith

class WgpuOffscreenRenderPass2d(
    val parentPass: OffscreenRenderPass2d,
    numSamples: Int,
    backend: RenderBackendWebGpu
) : WgpuRenderPass(
    parentPass.depthTexture?.let { GPUTextureFormat.depth32float },
    numSamples,
    backend
), OffscreenPass2dImpl {

    override val isReverseDepth: Boolean = false

    override val colorTargetFormats = parentPass.colorTextures.map { it.props.format.wgpu }

    private val colorAttachments = List(parentPass.numColorTextures) { RenderAttachment(parentPass.colorTextures[it], false) }
    private val depthAttachment = parentPass.depthTexture?.let { RenderAttachment(it, false) }

    init {
        releaseWith(parentPass)
    }

    override fun applySize(width: Int, height: Int) {
        colorAttachments.forEach { it.applySize(width, height) }
    }

    override fun release() { }

    override fun draw(ctx: KoolContext) {
        val gpuColorAttachments = colorAttachments.mapIndexed { i, colorTex ->
            GPURenderPassColorAttachment(
                view = colorTex.view,
                clearValue = parentPass.mainView.clearColors[i]?.let { GPUColorDict(it) }
            )
        }.toTypedArray()

        render(parentPass, gpuColorAttachments, depthAttachment?.view)

        if (parentPass.copyTargetsColor.isNotEmpty()) {
            val encoder = device.createCommandEncoder()
            parentPass.copyTargetsColor.forEach { tex ->
                val wgpuTex = (tex.loadedTexture as WgpuTextureResource?)
                    ?: colorAttachments[0].createTexture(
                        width = parentPass.width,
                        height = parentPass.height,
                        usage = GPUTextureUsage.COPY_DST or GPUTextureUsage.TEXTURE_BINDING or GPUTextureUsage.RENDER_ATTACHMENT,
                    ).also { tex.loadedTexture = WgpuLoadedTexture(it) }
                tex.loadingState = Texture.LoadingState.LOADED

                encoder.copyTextureToTexture(
                    source = GPUImageCopyTexture(colorAttachments[0].gpuTexture.gpuTexture),
                    destination = GPUImageCopyTexture(wgpuTex.gpuTexture),
                    intArrayOf(parentPass.width, parentPass.height)
                )
            }
            device.queue.submit(arrayOf(encoder.finish()))
        }
    }

    private inner class RenderAttachment(val texture: Texture, val isDepth: Boolean) : BaseReleasable() {
        var gpuTexture = createTexture(parentPass.width, parentPass.height)
        var view = gpuTexture.gpuTexture.createView()

        init {
            releaseWith(this@WgpuOffscreenRenderPass2d)
            texture.loadedTexture = WgpuLoadedTexture(gpuTexture)
            texture.loadingState = Texture.LoadingState.LOADED
        }

        fun applySize(width: Int, height: Int) {
            gpuTexture.release()
            gpuTexture = createTexture(width, height)
            view = gpuTexture.gpuTexture.createView()

            texture.loadedTexture = WgpuLoadedTexture(gpuTexture)
        }

        fun createTexture(
            width: Int,
            height: Int,
            usage: Int = GPUTextureUsage.COPY_SRC or GPUTextureUsage.TEXTURE_BINDING or GPUTextureUsage.RENDER_ATTACHMENT,
        ): WgpuTextureResource = backend.createTexture(
            GPUTextureDescriptor(
                label = "${parentPass.name}.colorAttachment",
                size = intArrayOf(width, height),
                format = if (isDepth) GPUTextureFormat.depth32float else texture.props.format.wgpu,
                usage = usage,
                mipLevelCount = parentPass.mipLevels,
                sampleCount = numSamples
            ),
            texture
        )

        override fun release() {
            super.release()
            texture.loadingState = Texture.LoadingState.NOT_LOADED
            texture.loadedTexture = null
        }
    }
}