package de.fabmax.kool.platform.webgl

import de.fabmax.kool.drawqueue.DrawCommand
import de.fabmax.kool.gl.*
import de.fabmax.kool.pipeline.CubeMapSampler
import de.fabmax.kool.pipeline.Pipeline
import de.fabmax.kool.pipeline.TextureSampler
import de.fabmax.kool.pipeline.UniformBuffer
import de.fabmax.kool.platform.JsContext
import de.fabmax.kool.scene.Mesh
import de.fabmax.kool.shading.VboBinder
import de.fabmax.kool.util.createUint16Buffer
import org.khronos.webgl.WebGLProgram
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE0
import org.khronos.webgl.WebGLUniformLocation

class CompiledShader(val prog: WebGLProgram?, pipeline: Pipeline, val ctx: JsContext) {
    val attributeLocations = mutableMapOf<String, Int>()
    val uniformLocations = mutableMapOf<String, WebGLUniformLocation?>()
    val instances = mutableMapOf<Long, ShaderInstance>()

    private var firstUse = true

    init {
        pipeline.vertexLayout.bindings.forEach { bnd ->
            bnd.attributes.forEach { attr ->
                attributeLocations[attr.name] = attr.location
            }
        }
        pipeline.descriptorSetLayouts.forEach { set ->
            set.descriptors.forEach { desc ->
                if (desc is UniformBuffer) {
                    desc.uniforms.forEach { uniformLocations[it.name] = ctx.gl.getUniformLocation(prog, it.name) }
                } else {
                    // sampler (texture or cube map
                    uniformLocations[desc.name] = ctx.gl.getUniformLocation(prog, desc.name)
                }
            }
        }
    }

    fun use() {
        ctx.gl.useProgram(prog)
        if (firstUse) {
            firstUse = false
            attributeLocations.values.forEach { loc -> ctx.gl.enableVertexAttribArray(loc) }
        }
    }

    fun bindInstance(cmd: DrawCommand): ShaderInstance {
        val pipelineInst = cmd.pipeline!!
        val inst = instances.getOrPut(pipelineInst.pipelineInstanceId) { ShaderInstance(cmd.mesh, pipelineInst) }
        inst.bindInstance(cmd)
        return inst
    }

    inner class ShaderInstance(val mesh: Mesh, pipeline: Pipeline) {
        private val ubos = mutableListOf<UniformBuffer>()
        private val textures = mutableListOf<TextureSampler>()
        private val cubeMaps = mutableListOf<CubeMapSampler>()
        private val mappings = mutableListOf<MappedUniform>()
        private val attributeBinders = mutableMapOf<String, AttributeOnLocation>()

        private var dataBufferF: BufferResource? = null
        private var dataBufferI: BufferResource? = null
        private var indexBuffer: BufferResource? = null

        private var nextTexUnit = TEXTURE0

        var indexType = 0
        var numIndices = 0
        var primitiveType = 0

        init {
            pipeline.descriptorSetLayouts.forEach { set ->
                set.descriptors.forEach { desc ->
                    when (desc) {
                        is UniformBuffer -> mapUbo(desc)
                        is TextureSampler -> mapTexture(desc)
                        is CubeMapSampler -> mapCubeMap(desc)
                        else -> TODO()
                    }
                }
            }
        }

        private fun mapUbo(ubo: UniformBuffer) {
            ubos.add(ubo)
            ubo.uniforms.forEach { mappings += MappedUniform.mappedUniform(it, uniformLocations[it.name]) }
        }

        private fun mapTexture(tex: TextureSampler) {
            textures.add(tex)
            mappings += MappedUniformTex2d(tex, nextTexUnit++, uniformLocations[tex.name])
        }

        private fun mapCubeMap(cubeMap: CubeMapSampler) {
            cubeMaps.add(cubeMap)
            mappings += MappedUniformCubeMap(cubeMap, nextTexUnit++, uniformLocations[cubeMap.name])
        }

        fun bindInstance(drawCmd: DrawCommand) {
            // call onUpdate callbacks
            for (i in ubos.indices) {
                ubos[i].onUpdate?.invoke(ubos[i], drawCmd)
            }
            for (i in textures.indices) {
                textures[i].onUpdate?.invoke(textures[i], drawCmd)
            }
            for (i in cubeMaps.indices) {
                cubeMaps[i].onUpdate?.invoke(cubeMaps[i], drawCmd)
            }

            // update unitform values
            for (i in mappings.indices) {
                mappings[i].setUniform(ctx)
            }

            // update vertex data
            checkBuffers()

            // bind vertex data
            indexBuffer?.bind(ctx)
            attributeBinders.values.forEach { it.vbo.bindAttribute(it.loc, ctx) }
        }

        private fun checkBuffers() {
            val md = mesh.meshData
            if (indexBuffer == null) {
                indexBuffer = BufferResource.create(GL_ELEMENT_ARRAY_BUFFER, ctx)
            }
            var hasIntData = false
            if (dataBufferF == null) {
                dataBufferF = BufferResource.create(GL_ARRAY_BUFFER, ctx)
                for (vertexAttrib in md.vertexAttributes) {
                    if (vertexAttrib.type.isInt) {
                        hasIntData = true
                    } else {
                        attributeLocations[vertexAttrib.glslSrcName]?.let { location ->
                            val vbo = VboBinder(dataBufferF!!, vertexAttrib.type.size / 4,
                                    md.vertexList.strideBytesF, md.vertexList.attributeOffsets[vertexAttrib]!! / 4, GL_FLOAT)
                            attributeBinders[vertexAttrib.glslSrcName] = AttributeOnLocation(vbo, location)
                        }
                    }
                }
            }
            if (hasIntData && dataBufferI == null) {
                dataBufferI = BufferResource.create(GL_ARRAY_BUFFER, ctx)
                for (vertexAttrib in md.vertexAttributes) {
                    if (vertexAttrib.type.isInt) {
                        attributeLocations[vertexAttrib.glslSrcName]?.let { location ->
                            val vbo = VboBinder(dataBufferI!!, vertexAttrib.type.size / 4,
                                    md.vertexList.strideBytesI, md.vertexList.attributeOffsets[vertexAttrib]!! / 4, GL_INT)
                            attributeBinders[vertexAttrib.glslSrcName] = AttributeOnLocation(vbo, location)
                        }
                    }
                }
            }

            if (md.isSyncRequired && !md.isBatchUpdate) {
                if (md.isRebuildBoundsOnSync) {
                    md.rebuildBounds()
                }
                if (!ctx.glCapabilities.uint32Indices) {
                    // convert index buffer to uint16
                    val uint16Buffer = createUint16Buffer(md.numIndices)
                    for (i in 0 until md.vertexList.indices.position) {
                        uint16Buffer.put(md.vertexList.indices[i].toShort())
                    }
                    indexType = GL_UNSIGNED_SHORT
                    indexBuffer?.setData(uint16Buffer, md.usage, ctx)
                } else {
                    indexType = GL_UNSIGNED_INT
                    indexBuffer?.setData(md.vertexList.indices, md.usage, ctx)
                }
                primitiveType = md.primitiveType
                numIndices = md.numIndices
                dataBufferF?.setData(md.vertexList.dataF, md.usage, ctx)
                dataBufferI?.setData(md.vertexList.dataI, md.usage, ctx)
                md.isSyncRequired = false
            }
        }
    }

    private data class AttributeOnLocation(val vbo: VboBinder, val loc: Int)
}