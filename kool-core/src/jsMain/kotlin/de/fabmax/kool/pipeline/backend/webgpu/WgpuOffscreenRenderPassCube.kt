package de.fabmax.kool.pipeline.backend.webgpu

import de.fabmax.kool.pipeline.*
import de.fabmax.kool.util.BaseReleasable
import de.fabmax.kool.util.releaseWith

class WgpuOffscreenRenderPassCube(
    val parentPass: OffscreenRenderPassCube,
    numSamples: Int,
    backend: RenderBackendWebGpu
) : WgpuRenderPass<OffscreenRenderPassCube>(GPUTextureFormat.depth32float, numSamples, backend), OffscreenPassCubeImpl {

    override val colorTargetFormats = parentPass.colorTextures.map { it.props.format.wgpu }

    private val colorAttachments = List(parentPass.colorTextures.size) {
        RenderAttachment(parentPass.colorTextures[it], false, "${parentPass.name}.color[$it]")
    }
    private val depthAttachment: RenderAttachment?

    private var copySrcFlag = 0

    init {
        val depthTex = when (parentPass.depthAttachment) {
            OffscreenRenderPass.DepthAttachmentRender -> TextureCube(
                TextureProps(generateMipMaps = false, defaultSamplerSettings = SamplerSettings().clamped()),
                "${parentPass.name}:render-depth"
            )
            else -> parentPass.depthTexture
        }
        depthAttachment = depthTex?.let { RenderAttachment(it, true,  it.name) }
        releaseWith(parentPass)
    }

    override fun applySize(width: Int, height: Int) {
        colorAttachments.forEach { it.recreate(width, height) }
        depthAttachment?.recreate(width, height)
    }

    override fun release() {
        colorAttachments.forEach { it.release() }
        depthAttachment?.release()
    }

    fun draw(encoder: GPUCommandEncoder) {
        val isCopySrc = parentPass.frameCopies.isNotEmpty() || parentPass.views.any { it.frameCopies.isNotEmpty() }
        if (isCopySrc && copySrcFlag == 0) {
            // recreate attachment textures with COPY_SRC flag set
            copySrcFlag = GPUTextureUsage.COPY_SRC
            colorAttachments.forEach { it.recreate(parentPass.width, parentPass.height) }
            depthAttachment?.recreate(parentPass.width, parentPass.height)
        }

        render(parentPass, encoder)
    }

    override fun generateMipLevels(encoder: GPUCommandEncoder) {
        colorAttachments.forEach {
            backend.textureLoader.mipmapGenerator.generateMipLevels(it.descriptor, it.gpuTexture.gpuTexture, encoder)
        }
    }

    override fun copy(frameCopy: FrameCopy, encoder: GPUCommandEncoder) {
        if (frameCopy.isCopyColor) {
            for (i in frameCopy.colorCopy.indices) {
                colorAttachments[i].copyToTexture(frameCopy.colorCopy[i] as TextureCube, encoder)
            }
        }
        if (frameCopy.isCopyDepth) {
            depthAttachment?.copyToTexture(frameCopy.depthCopyCube, encoder)
        }
    }

    override fun getRenderAttachments(renderPass:OffscreenRenderPassCube, viewIndex: Int, mipLevel: Int, forceLoad: Boolean): RenderAttachments {
        val colors = colorAttachments.mapIndexed { i, colorTex ->
            val colorLoadOp = when {
                forceLoad -> GPULoadOp.load
                renderPass.clearColor == null -> GPULoadOp.load
                else -> GPULoadOp.clear
            }
            val clearColor = if (colorLoadOp == GPULoadOp.load) null else parentPass.clearColors[i]?.let { GPUColorDict(it) }

            GPURenderPassColorAttachment(
                view = colorTex.getView(viewIndex, mipLevel),
                loadOp = colorLoadOp,
                clearValue = clearColor,
            )
        }.toTypedArray()

        val depthLoadOp = when {
            forceLoad -> GPULoadOp.load
            renderPass.clearDepth -> GPULoadOp.clear
            else -> GPULoadOp.load
        }
        val depth = depthAttachment?.let {
            GPURenderPassDepthStencilAttachment(
                view = it.getView(viewIndex, mipLevel),
                depthLoadOp = depthLoadOp,
                depthStoreOp = GPUStoreOp.store,
                depthClearValue = if (renderPass.isReverseDepth) 0f else 1f
            )
        }
        return RenderAttachments(colors, depth)
    }

    private inner class RenderAttachment(val texture: TextureCube, val isDepth: Boolean, val name: String) : BaseReleasable() {
        var descriptor: GPUTextureDescriptor
        var gpuTexture: WgpuTextureResource

        init {
            val (desc, tex) = createTexture(
                width = parentPass.width,
                height = parentPass.height,
                usage = GPUTextureUsage.TEXTURE_BINDING or GPUTextureUsage.RENDER_ATTACHMENT,
            )
            descriptor = desc
            gpuTexture = tex

            texture.loadedTexture = WgpuLoadedTexture(gpuTexture)
            texture.loadingState = Texture.LoadingState.LOADED
        }

        fun recreate(width: Int, height: Int) {
            val (desc, tex) = createTexture(
                width = width,
                height = height,
                usage = GPUTextureUsage.TEXTURE_BINDING or GPUTextureUsage.RENDER_ATTACHMENT or copySrcFlag
            )
            descriptor = desc
            gpuTexture = tex

            texture.loadedTexture?.release()
            texture.loadedTexture = WgpuLoadedTexture(gpuTexture)
        }

        fun getView(face: Int, mipLevel: Int): GPUTextureView {
            // View is newly created every time it is needed. Feels a bit suboptimal but caching multiple views for
            // different baseArrayLayers results in all views pointing to the last baseArrayLayer.
            // Not sure if this is a bug or intended WebGPU behavior...
            return gpuTexture.gpuTexture.createView(
                baseArrayLayer = face,
                baseMipLevel = mipLevel,
                mipLevelCount = 1,
                arrayLayerCount = 1,
                dimension = GPUTextureViewDimension.view2d
            )
        }

        fun copyToTexture(target: TextureCube, encoder: GPUCommandEncoder) {
            var copyDst = (target.loadedTexture as WgpuLoadedTexture?)
            if (copyDst == null || copyDst.width != parentPass.width || copyDst.height != parentPass.height) {
                copyDst?.release()
                val (_, gpuTex) = createTexture(
                    width = parentPass.width,
                    height = parentPass.height,
                    usage = GPUTextureUsage.COPY_DST or GPUTextureUsage.TEXTURE_BINDING or GPUTextureUsage.RENDER_ATTACHMENT,
                    texture = target
                )
                copyDst = WgpuLoadedTexture(gpuTex)
                target.loadedTexture = copyDst
                target.loadingState = Texture.LoadingState.LOADED
            }
            backend.textureLoader.copyTexture2d(gpuTexture.gpuTexture, copyDst.texture.gpuTexture, parentPass.numTextureMipLevels, encoder)
        }

        private fun createTexture(
            width: Int,
            height: Int,
            usage: Int,
            texture: Texture = this.texture
        ): Pair<GPUTextureDescriptor, WgpuTextureResource> {
            val desc = GPUTextureDescriptor(
                label = "${parentPass.name}.colorAttachment",
                size = intArrayOf(width, height, 6),
                format = if (isDepth) GPUTextureFormat.depth32float else texture.props.format.wgpu,
                usage = usage,
                dimension = GPUTextureDimension.texture2d,
                mipLevelCount = parentPass.numRenderMipLevels,
                sampleCount = numSamples,
            )
            val tex = backend.createTexture(desc, texture)
            return desc to tex
        }

        override fun release() {
            if (!isReleased) {
                super.release()
                texture.loadedTexture?.release()
                texture.loadedTexture = null
                texture.loadingState = Texture.LoadingState.NOT_LOADED
            }
        }
    }
}