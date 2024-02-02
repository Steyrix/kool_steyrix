package de.fabmax.kool.pipeline.backend.webgpu

import de.fabmax.kool.pipeline.*
import de.fabmax.kool.pipeline.backend.stats.PipelineInfo
import de.fabmax.kool.pipeline.drawqueue.DrawCommand
import de.fabmax.kool.scene.Mesh
import de.fabmax.kool.util.BaseReleasable
import de.fabmax.kool.util.RenderLoop
import de.fabmax.kool.util.checkIsNotReleased
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WgpuDrawPipeline(
    val drawPipeline: DrawPipeline,
    private val vertexShaderModule: GPUShaderModule,
    private val fragmentShaderModule: GPUShaderModule,
    private val renderPass: WgpuRenderPass,
    private val backend: RenderBackendWebGpu,
): BaseReleasable(), PipelineBackend {
    private val device: GPUDevice get() = backend.device
    private val pipelineInfo = PipelineInfo(drawPipeline)

    private val locations = WgslLocations(drawPipeline.bindGroupLayouts, drawPipeline.vertexLayout)

    private val bindGroupLayouts: List<GPUBindGroupLayout> = createBindGroupLayouts(drawPipeline)
    private val pipelineLayout: GPUPipelineLayout = createPipelineLayout(drawPipeline)
    private val vertexBufferLayout: List<GPUVertexBufferLayout> = createVertexBufferLayout(drawPipeline)
    private val renderPipeline: GPURenderPipeline = createRenderPipeline(drawPipeline)

    private val users = mutableSetOf<Int>()

    private fun createBindGroupLayouts(pipeline: DrawPipeline): List<GPUBindGroupLayout> {
        return pipeline.bindGroupLayouts.asList.map { group ->
            val layoutEntries = buildList {
                group.bindings.forEach { binding ->
                    val visibility = binding.stages.fold(0) { acc, stage ->
                        acc or when (stage) {
                            ShaderStage.VERTEX_SHADER -> GPUShaderStage.VERTEX
                            ShaderStage.FRAGMENT_SHADER -> GPUShaderStage.FRAGMENT
                            ShaderStage.COMPUTE_SHADER -> GPUShaderStage.COMPUTE
                            else -> error("unsupported shader stage: $stage")
                        }
                    }
                    val location = locations[binding]

                    when (binding) {
                        is UniformBufferLayout -> add(makeLayoutEntryBuffer(location, visibility))
                        is Texture1dLayout -> addAll(makeLayoutEntriesTexture(binding, location, visibility, GPUTextureViewDimension.view1d))
                        is Texture2dLayout -> addAll(makeLayoutEntriesTexture(binding, location, visibility, GPUTextureViewDimension.view2d))
                        is Texture3dLayout -> addAll(makeLayoutEntriesTexture(binding, location, visibility, GPUTextureViewDimension.view3d))
                        is TextureCubeLayout -> addAll(makeLayoutEntriesTexture(binding, location, visibility, GPUTextureViewDimension.viewCube))

                        is StorageTexture1dLayout -> TODO("StorageTexture1dLayout")
                        is StorageTexture2dLayout -> TODO("StorageTexture2dLayout")
                        is StorageTexture3dLayout -> TODO("StorageTexture3dLayout")
                    }
                }
            }

            device.createBindGroupLayout(
                label = "${pipeline.name}-bindGroupLayout[${group.scope}]",
                entries = layoutEntries.toTypedArray()
            )
        }
    }

    private fun makeLayoutEntryBuffer(location: WgslLocations.Location, visibility: Int) = GPUBindGroupLayoutEntryBuffer(
        binding = location.binding,
        visibility = visibility,
        buffer = GPUBufferBindingLayout()
    )

    private fun makeLayoutEntriesTexture(
        binding: TextureLayout,
        location: WgslLocations.Location,
        visibility: Int,
        dimension: GPUTextureViewDimension
    ): List<GPUBindGroupLayoutEntry> {
        val samplerType = when (binding) {
            is Texture2dLayout -> if (binding.isDepthTexture) GPUSamplerBindingType.comparison else GPUSamplerBindingType.filtering
            is TextureCubeLayout -> if (binding.isDepthTexture) GPUSamplerBindingType.comparison else GPUSamplerBindingType.filtering
            else -> GPUSamplerBindingType.filtering
        }
        val texSampleType = when (binding) {
            is Texture2dLayout -> if (binding.isDepthTexture) GPUTextureSampleType.depth else GPUTextureSampleType.float
            is TextureCubeLayout -> if (binding.isDepthTexture) GPUTextureSampleType.depth else GPUTextureSampleType.float
            else -> GPUTextureSampleType.float
        }
        return listOf(
            GPUBindGroupLayoutEntrySampler(
                location.binding,
                visibility,
                GPUSamplerBindingLayout(samplerType)
            ),
            GPUBindGroupLayoutEntryTexture(
                location.binding + 1,
                visibility,
                GPUTextureBindingLayout(viewDimension = dimension, sampleType = texSampleType)
            )
        )
    }

    private fun createPipelineLayout(pipeline: DrawPipeline): GPUPipelineLayout {
        return device.createPipelineLayout(GPUPipelineLayoutDescriptor(
            label = "${pipeline.name}-bindGroupLayout",
            bindGroupLayouts = bindGroupLayouts.toTypedArray()
        ))
    }

    private fun createVertexBufferLayout(pipeline: DrawPipeline): List<GPUVertexBufferLayout> {
        return pipeline.vertexLayout.bindings
            .sortedBy { it.inputRate.name }     // INSTANCE first, VERTEX second
            .map { vertexBinding ->
                val attributes = vertexBinding.vertexAttributes.flatMap { attr ->
                    val (format, stride) = when (attr.type) {
                        GpuType.FLOAT1 -> GPUVertexFormat.float32 to 4
                        GpuType.FLOAT2 -> GPUVertexFormat.float32x2 to 8
                        GpuType.FLOAT3 -> GPUVertexFormat.float32x3 to 12
                        GpuType.FLOAT4 -> GPUVertexFormat.float32x4 to 16
                        GpuType.INT1 -> TODO("needs extra buffer") //GPUVertexFormat.sint32 to 4
                        GpuType.INT2 -> TODO("needs extra buffer") //GPUVertexFormat.sint32x2 to 8
                        GpuType.INT3 -> TODO("needs extra buffer") //GPUVertexFormat.sint32x3 to 12
                        GpuType.INT4 -> TODO("needs extra buffer") //GPUVertexFormat.sint32x4 to 16

                        GpuType.MAT2 -> GPUVertexFormat.float32x2 to 8
                        GpuType.MAT3 -> GPUVertexFormat.float32x3 to 12
                        GpuType.MAT4 -> GPUVertexFormat.float32x4 to 16
                    }

                    locations[attr].mapIndexed { i, loc ->
                        GPUVertexAttribute(
                            format = format,
                            offset = attr.bufferOffset.toLong() + stride * i,
                            shaderLocation = loc.location
                        )
                    }
                }

            GPUVertexBufferLayout(
                arrayStride = vertexBinding.strideBytes.toLong(),
                attributes = attributes.toTypedArray(),
                stepMode = when (vertexBinding.inputRate) {
                    InputRate.VERTEX -> GPUVertexStepMode.vertex
                    InputRate.INSTANCE -> GPUVertexStepMode.instance
                }
            )
        }
    }

    private fun createRenderPipeline(pipeline: DrawPipeline): GPURenderPipeline {
        val shaderCode = pipeline.shaderCode as RenderBackendWebGpu.WebGpuShaderCode
        val vertexState = GPUVertexState(
            module = vertexShaderModule,
            entryPoint = shaderCode.vertexEntryPoint,
            buffers = vertexBufferLayout.toTypedArray()
        )

        val blendMode = when (pipeline.pipelineConfig.blendMode) {
            BlendMode.DISABLED -> null
            BlendMode.BLEND_ADDITIVE -> GPUBlendState(
                color = GPUBlendComponent(srcFactor = GPUBlendFactor.one, dstFactor = GPUBlendFactor.one),
                alpha = GPUBlendComponent(),
            )
            BlendMode.BLEND_MULTIPLY_ALPHA -> GPUBlendState(
                color = GPUBlendComponent(srcFactor = GPUBlendFactor.srcAlpha, dstFactor = GPUBlendFactor.oneMinusSrcAlpha),
                alpha = GPUBlendComponent(),
            )
            BlendMode.BLEND_PREMULTIPLIED_ALPHA -> GPUBlendState(
                color = GPUBlendComponent(srcFactor = GPUBlendFactor.one, dstFactor = GPUBlendFactor.oneMinusSrcAlpha),
                alpha = GPUBlendComponent(),
            )
        }

        val fragmentState = GPUFragmentState(
            module = fragmentShaderModule,
            entryPoint = shaderCode.fragmentEntryPoint,
            targets = renderPass.colorTargetFormats.map { GPUColorTargetState(it, blendMode) }.toTypedArray()
        )

        val primitiveState = GPUPrimitiveState(
            topology = pipeline.vertexLayout.primitiveType.wgpu,
            cullMode = pipeline.pipelineConfig.cullMethod.wgpu
        )

        val depthStencil = renderPass.depthFormat?.let { depthFormat ->
            GPUDepthStencilState(
                format = depthFormat,
                depthWriteEnabled = if (pipeline.pipelineConfig.depthTest == DepthCompareOp.DISABLED) false else pipeline.pipelineConfig.isWriteDepth,
                depthCompare = if (pipeline.pipelineConfig.depthTest == DepthCompareOp.DISABLED) GPUCompareFunction.always else pipeline.pipelineConfig.depthTest.wgpu
            )
        }

        return device.createRenderPipeline(
            label = "${pipeline.name}-layout",
            layout = pipelineLayout,
            vertex = vertexState,
            fragment = fragmentState,
            depthStencil = depthStencil,
            primitive = primitiveState,
            multisample = GPUMultisampleState(renderPass.numSamples)
        )
    }

    fun bind(cmd: DrawCommand, encoder: GPURenderPassEncoder): Boolean {
        users.add(cmd.mesh.id)

        val pipeline = cmd.pipeline!!
        val pipelineData = pipeline.pipelineData
        val viewData = cmd.queue.view.viewPipelineData.getPipelineData(pipeline)
        val meshData = cmd.mesh.meshPipelineData.getPipelineData(pipeline)

        if (!checkTextures(pipelineData) || !checkTextures(viewData) || !checkTextures(meshData)) {
            return false
        }

        encoder.setPipeline(renderPipeline)
        viewData.getOrCreateWgpuData().bind(encoder, viewData, cmd.queue.renderPass)
        pipelineData.getOrCreateWgpuData().bind(encoder, pipelineData, cmd.queue.renderPass)
        meshData.getOrCreateWgpuData().bind(encoder, meshData, cmd.queue.renderPass)
        bindVertexBuffers(encoder, cmd.mesh)
        return true
    }

    private fun checkTextures(bindGroupData: BindGroupData): Boolean {
        var isComplete = true
        bindGroupData.bindings
            .filterIsInstance<BindGroupData.TextureBindingData<*>>()
            .map { it.texture }
            .filter { it?.loadingState != Texture.LoadingState.LOADED }
            .forEach {
                if (it == null || !checkLoadingState(it)) {
                    isComplete = false
                }
            }
        return isComplete
    }

    private fun checkLoadingState(texture: Texture): Boolean {
        texture.checkIsNotReleased()
        if (texture.loadingState == Texture.LoadingState.NOT_LOADED) {
            when (texture.loader) {
                is AsyncTextureLoader -> {
                    texture.loadingState = Texture.LoadingState.LOADING
                    CoroutineScope(Dispatchers.RenderLoop).launch {
                        val texData = texture.loader.loadTextureDataAsync().await()
                        backend.textureLoader.loadTexture(texture, texData)
                    }
                }
                is SyncTextureLoader -> {
                    val texData = texture.loader.loadTextureDataSync()
                    backend.textureLoader.loadTexture(texture, texData)
                }
                is BufferedTextureLoader -> {
                    backend.textureLoader.loadTexture(texture, texture.loader.data)
                }
                else -> {
                    // loader is null
                    texture.loadingState = Texture.LoadingState.LOADING_FAILED
                }
            }
        }
        return texture.loadingState == Texture.LoadingState.LOADED
    }

    private fun BindGroupData.getOrCreateWgpuData(): WgpuBindGroupData {
        if (gpuData == null) {
            gpuData = WgpuBindGroupData(this, bindGroupLayouts[layout.group], locations, backend)
        }
        return gpuData as WgpuBindGroupData
    }

    private fun bindVertexBuffers(encoder: GPURenderPassEncoder, mesh: Mesh) {
        if (mesh.geometry.gpuGeometry == null) {
            mesh.geometry.gpuGeometry = WgpuGeometry(mesh, backend)
        }
        val gpuGeom = mesh.geometry.gpuGeometry as WgpuGeometry
        gpuGeom.checkBuffers()

        var slot = 0
        gpuGeom.instanceBuffer?.let { encoder.setVertexBuffer(slot++, it) }
        encoder.setVertexBuffer(slot++, gpuGeom.floatBuffer)
        gpuGeom.intBuffer?.let { encoder.setVertexBuffer(slot, it) }
        encoder.setIndexBuffer(gpuGeom.indexBuffer, GPUIndexFormat.uint32)
    }

    override fun removeUser(user: Any) {
        (user as? Mesh)?.let { users.remove(it.id) }
        if (users.isEmpty()) {
            release()
        }
    }

    override fun release() {
        if (!isReleased) {
            super.release()
            if (!drawPipeline.isReleased) {
                drawPipeline.release()
            }
            backend.pipelineManager.removeDrawPipeline(this)
            pipelineInfo.deleted()
        }
    }
}