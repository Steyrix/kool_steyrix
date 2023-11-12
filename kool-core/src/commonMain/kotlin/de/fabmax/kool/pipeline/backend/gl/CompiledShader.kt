package de.fabmax.kool.pipeline.backend.gl

import de.fabmax.kool.KoolContext
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.pipeline.drawqueue.DrawCommand
import de.fabmax.kool.scene.MeshInstanceList
import de.fabmax.kool.scene.geometry.IndexedVertexList
import de.fabmax.kool.scene.geometry.PrimitiveType
import de.fabmax.kool.util.logE

class CompiledShader(val program: GlProgram, val pipeline: Pipeline, val backend: RenderBackendGl) {

    private val pipelineId = pipeline.pipelineHash.toLong()

    private val attributes = mutableMapOf<String, VertexLayout.VertexAttribute>()
    private val instanceAttributes = mutableMapOf<String, VertexLayout.VertexAttribute>()
    private val uniformLocations = mutableMapOf<String, IntArray>()
    private val uboLayouts = mutableMapOf<String, ExternalBufferLayout>()
    private val instances = mutableMapOf<Long, ShaderInstance>()

    private val ctx: KoolContext = backend.ctx
    private val gl: GlApi = backend.gl

    init {
        pipeline.layout.vertices.bindings.forEach { bnd ->
            bnd.vertexAttributes.forEach { attr ->
                when (bnd.inputRate) {
                    InputRate.VERTEX -> attributes[attr.attribute.name] = attr
                    InputRate.INSTANCE -> instanceAttributes[attr.attribute.name] = attr
                }
            }
        }
        pipeline.layout.descriptorSets.forEach { set ->
            set.descriptors.forEach { desc ->
                when (desc) {
                    is UniformBuffer -> {
                        val blockIndex = gl.getUniformBlockIndex(program, desc.name)
                        if (blockIndex == gl.INVALID_INDEX) {
                            // descriptor does not describe an actual UBO but plain old uniforms...
                            desc.uniforms.forEach { uniformLocations[it.name] = intArrayOf(gl.getUniformLocation(program, it.name)) }
                        } else {
                            setupUboLayout(desc, blockIndex)
                        }
                    }
                    is TextureSampler1d -> {
                        uniformLocations[desc.name] = getUniformLocations(desc.name, desc.arraySize)
                    }
                    is TextureSampler2d -> {
                        uniformLocations[desc.name] = getUniformLocations(desc.name, desc.arraySize)
                    }
                    is TextureSampler3d -> {
                        uniformLocations[desc.name] = getUniformLocations(desc.name, desc.arraySize)
                    }
                    is TextureSamplerCube -> {
                        uniformLocations[desc.name] = getUniformLocations(desc.name, desc.arraySize)
                    }
                }
            }
        }
        pipeline.layout.pushConstantRanges.forEach { pcr ->
            pcr.pushConstants.forEach { pc ->
                // in OpenGL push constants are mapped to regular uniforms
                uniformLocations[pc.name] = intArrayOf(gl.getUniformLocation(program, pc.name))
            }
        }
    }

    private fun setupUboLayout(desc: UniformBuffer, blockIndex: Int) {
        val bufferSize = gl.getActiveUniformBlockParameter(program, blockIndex, gl.UNIFORM_BLOCK_DATA_SIZE)
        val uniformNames = desc.uniforms.map {
            if (it.size > 1) "${it.name}[0]" else it.name
        }.toTypedArray()

        val indices = gl.getUniformIndices(program, uniformNames)
        val offsets = gl.getActiveUniforms(program, indices, gl.UNIFORM_OFFSET)

        val sortedOffsets = offsets.sorted()
        val bufferPositions = Array(desc.uniforms.size) { i ->
            val off = offsets[i]
            val nextOffI = sortedOffsets.indexOf(off) + 1
            val nextOff = if (nextOffI < sortedOffsets.size) sortedOffsets[nextOffI] else bufferSize
            BufferPosition(off, nextOff - off)
        }

        gl.uniformBlockBinding(program, blockIndex, desc.binding)
        uboLayouts[desc.name] = ExternalBufferLayout(desc.uniforms, bufferPositions, bufferSize)
    }

    private fun getUniformLocations(name: String, arraySize: Int): IntArray {
        val locations = IntArray(arraySize)
        if (arraySize > 1) {
            for (i in 0 until arraySize) {
                locations[i] = gl.getUniformLocation(program, "$name[$i]")
            }
        } else {
            locations[0] = gl.getUniformLocation(program, name)
        }
        return locations
    }

    fun use() {
        gl.useProgram(program)
        attributes.values.forEach { attr ->
            for (i in 0 until attr.attribute.props.nSlots) {
                val location = attr.location + i
                gl.enableVertexAttribArray(location)
                gl.vertexAttribDivisor(location, 0)
            }
        }
        instanceAttributes.values.forEach { attr ->
            for (i in 0 until attr.attribute.props.nSlots) {
                val location = attr.location + i
                gl.enableVertexAttribArray(location)
                gl.vertexAttribDivisor(location, 1)
            }
        }
    }

    fun unUse() {
        attributes.values.forEach { attr ->
            for (i in 0 until attr.attribute.props.nSlots) {
                gl.disableVertexAttribArray(attr.location + i)
            }
        }
        instanceAttributes.values.forEach { attr ->
            for (i in 0 until attr.attribute.props.nSlots) {
                gl.disableVertexAttribArray(attr.location + i)
            }
        }
    }

    fun bindInstance(cmd: DrawCommand): ShaderInstance? {
        val pipelineInst = cmd.pipeline!!
        val inst = instances.getOrPut(pipelineInst.pipelineInstanceId) {
            ShaderInstance(cmd.geometry, cmd.mesh.instances, pipelineInst)
        }
        return if (inst.bindInstance(cmd)) { inst } else { null }
    }

    fun destroyInstance(pipeline: Pipeline) {
        instances.remove(pipeline.pipelineInstanceId)?.let {
            it.destroyInstance()
            ctx.engineStats.pipelineInstanceDestroyed(pipelineId)
        }
    }

    fun isEmpty(): Boolean = instances.isEmpty()

    fun destroy() {
        ctx.engineStats.pipelineDestroyed(pipelineId)
        gl.deleteProgram(program)
    }

    inner class ShaderInstance(var geometry: IndexedVertexList, val instances: MeshInstanceList?, val pipeline: Pipeline) {
        private val pushConstants = mutableListOf<PushConstantRange>()
        private val ubos = mutableListOf<UniformBuffer>()
        private val textures1d = mutableListOf<TextureSampler1d>()
        private val textures2d = mutableListOf<TextureSampler2d>()
        private val textures3d = mutableListOf<TextureSampler3d>()
        private val texturesCube = mutableListOf<TextureSamplerCube>()
        private val mappings = mutableListOf<MappedUniform>()
        private val attributeBinders = mutableListOf<GpuGeometryGl.AttributeBinder>()
        private val instanceAttribBinders = mutableListOf<GpuGeometryGl.AttributeBinder>()

        private var gpuGeometry: GpuGeometryGl? = null
        private var uboBuffers = mutableListOf<BufferResource>()

        private var nextTexUnit = gl.TEXTURE0

        val primitiveType = pipeline.layout.vertices.primitiveType.glElemType
        val indexType = gl.UNSIGNED_INT
        val numIndices: Int get() = gpuGeometry?.numIndices ?: 0

        init {
            pipeline.layout.descriptorSets.forEach { set ->
                set.descriptors.forEach { desc ->
                    when (desc) {
                        is UniformBuffer -> mapUbo(desc)
                        is TextureSampler1d -> mapTexture1d(desc)
                        is TextureSampler2d -> mapTexture2d(desc)
                        is TextureSampler3d -> mapTexture3d(desc)
                        is TextureSamplerCube -> mapTextureCube(desc)
                    }
                }
            }
            pipeline.layout.pushConstantRanges.forEach { pc ->
                mapPushConstants(pc)
            }
            createBuffers()
            ctx.engineStats.pipelineInstanceCreated(pipelineId)
        }

        private fun createBuffers() {
            var geom = geometry.gpuGeometry as? GpuGeometryGl
            if (geom == null || geom.isDisposed) {
                if (geom?.isDisposed == true) {
                    logE { "disposed geometry: ${pipeline.name}" }
                }
                geom = GpuGeometryGl(geometry, instances, backend)
                geometry.gpuGeometry = geom
            }
            gpuGeometry = geom

            attributeBinders += geom.createShaderVertexAttributeBinders(attributes)
            instanceAttribBinders += geom.createShaderInstanceAttributeBinders(instanceAttributes)

            mappings.filterIsInstance<MappedUbo>().forEach { mappedUbo ->
                val uboBuffer = BufferResource(gl.UNIFORM_BUFFER, backend)
                uboBuffers += uboBuffer
                mappedUbo.uboBuffer = uboBuffer
            }
        }

        private fun mapPushConstants(pc: PushConstantRange) {
            pushConstants.add(pc)
            pc.pushConstants.forEach { mappings += MappedUniform.mappedUniform(it, uniformLocations[it.name]!![0], gl) }
        }

        private fun mapUbo(ubo: UniformBuffer) {
            ubos.add(ubo)
            val uboLayout = uboLayouts[ubo.name]
            if (uboLayout != null) {
                mappings += MappedUbo(ubo, uboLayout, gl)

            } else {
                ubo.uniforms.forEach {
                    val location = uniformLocations[it.name]
                    if (location != null) {
                        mappings += MappedUniform.mappedUniform(it, location[0], gl)
                    } else {
                        logE { "Uniform location not present for uniform ${ubo.name}.${it.name}" }
                    }
                }
            }
        }

        private fun mapTexture1d(tex: TextureSampler1d) {
            textures1d.add(tex)
            uniformLocations[tex.name]?.let { locs ->
                mappings += MappedUniformTex1d(tex, nextTexUnit, locs, backend)
                nextTexUnit += locs.size
            }
        }

        private fun mapTexture2d(tex: TextureSampler2d) {
            textures2d.add(tex)
            uniformLocations[tex.name]?.let { locs ->
                mappings += MappedUniformTex2d(tex, nextTexUnit, locs, backend)
                nextTexUnit += locs.size
            }
        }

        private fun mapTexture3d(tex: TextureSampler3d) {
            textures3d.add(tex)
            uniformLocations[tex.name]?.let { locs ->
                mappings += MappedUniformTex3d(tex, nextTexUnit, locs, backend)
                nextTexUnit += locs.size
            }
        }

        private fun mapTextureCube(cubeMap: TextureSamplerCube) {
            texturesCube.add(cubeMap)
            uniformLocations[cubeMap.name]?.let { locs ->
                mappings += MappedUniformTexCube(cubeMap, nextTexUnit, locs, backend)
                nextTexUnit += locs.size
            }
        }

        fun bindInstance(drawCmd: DrawCommand): Boolean {
            if (geometry !== drawCmd.geometry) {
                geometry = drawCmd.geometry
                destroyBuffers()
            }

            // call onUpdate callbacks
            for (i in pipeline.onUpdate.indices) {
                pipeline.onUpdate[i].invoke(drawCmd)
            }
            for (i in pushConstants.indices) {
                pushConstants[i].onUpdate?.invoke(pushConstants[i], drawCmd)
            }
            for (i in ubos.indices) {
                ubos[i].onUpdate?.invoke(ubos[i], drawCmd)
            }
            for (i in textures1d.indices) {
                textures1d[i].onUpdate?.invoke(textures1d[i], drawCmd)
            }
            for (i in textures2d.indices) {
                textures2d[i].onUpdate?.invoke(textures2d[i], drawCmd)
            }
            for (i in textures3d.indices) {
                textures3d[i].onUpdate?.invoke(textures3d[i], drawCmd)
            }
            for (i in texturesCube.indices) {
                texturesCube[i].onUpdate?.invoke(texturesCube[i], drawCmd)
            }

            // update geometry buffers (vertex + instance data)
            gpuGeometry?.checkBuffers()

            // bind uniform values
            var uniformsValid = true
            for (i in mappings.indices) {
                uniformsValid = uniformsValid && mappings[i].setUniform()
            }

            if (uniformsValid) {
                // bind vertex data
                gpuGeometry?.indexBuffer?.bind()
                attributeBinders.forEach { it.bindAttribute(it.loc) }
                instanceAttribBinders.forEach { it.bindAttribute(it.loc) }
            }
            return uniformsValid
        }

        private fun destroyBuffers() {
            attributeBinders.clear()
            instanceAttribBinders.clear()
            uboBuffers.forEach { it.delete() }
            gpuGeometry = null
            uboBuffers.clear()
        }

        fun destroyInstance() {
            destroyBuffers()

            pushConstants.clear()
            ubos.clear()
            textures1d.clear()
            textures2d.clear()
            textures3d.clear()
            texturesCube.clear()
            mappings.clear()
        }
    }

    private val PrimitiveType.glElemType: Int get() = when (this) {
        PrimitiveType.LINES -> gl.LINES
        PrimitiveType.POINTS -> gl.POINTS
        PrimitiveType.TRIANGLES -> gl.TRIANGLES
    }
}