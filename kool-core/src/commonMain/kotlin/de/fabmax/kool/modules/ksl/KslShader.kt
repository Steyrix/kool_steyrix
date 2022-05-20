package de.fabmax.kool.modules.ksl

import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.*
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.scene.Mesh
import kotlin.reflect.KProperty

open class KslShader(val program: KslProgram, val pipelineConfig: PipelineConfig) : Shader() {

    val uniforms = mutableMapOf<String, Uniform<*>>()
    val texSamplers1d = mutableMapOf<String, TextureSampler1d>()
    val texSamplers2d = mutableMapOf<String, TextureSampler2d>()
    val texSamplers3d = mutableMapOf<String, TextureSampler3d>()
    val texSamplersCube = mutableMapOf<String, TextureSamplerCube>()

    private val connectUniformListeners = mutableListOf<ConnectUniformListener>()

    override fun onPipelineSetup(builder: Pipeline.Builder, mesh: Mesh, ctx: KoolContext) {
        // prepare shader model for generating source code, also updates program dependencies (e.g. which
        // uniform is used by which shader stage)
        program.prepareGenerate()
        if (program.dumpCode) {
            program.vertexStage.hierarchy.printHierarchy()
        }

        setupAttributes(mesh, builder)
        setupUniforms(builder)

        builder.blendMode = pipelineConfig.blendMode
        builder.cullMethod = pipelineConfig.cullMethod
        builder.depthTest = pipelineConfig.depthTest
        builder.isWriteDepth = pipelineConfig.isWriteDepth
        builder.lineWidth = pipelineConfig.lineWidth

        builder.name = program.name
        builder.shaderCodeGenerator = { ctx.generateKslShader(this, it) }
        super.onPipelineSetup(builder, mesh, ctx)
    }

    override fun onPipelineCreated(pipeline: Pipeline, mesh: Mesh, ctx: KoolContext) {
        pipeline.layout.descriptorSets.forEach { descSet ->
            descSet.descriptors.forEach { desc ->
                when (desc) {
                    is UniformBuffer -> desc.uniforms.forEach { uniforms[it.name] = it }
                    is TextureSampler1d -> texSamplers1d[desc.name] = desc
                    is TextureSampler2d -> texSamplers2d[desc.name] = desc
                    is TextureSampler3d -> texSamplers3d[desc.name] = desc
                    is TextureSamplerCube -> texSamplersCube[desc.name] = desc
                }
            }
        }

        pipeline.onUpdate += { cmd ->
            for (i in program.shaderListeners.indices) {
                program.shaderListeners[i].onUpdate(cmd)
            }
        }
        program.shaderListeners.forEach { it.onShaderCreated(this, pipeline, ctx) }
        connectUniformListeners.forEach { it.connect() }

        super.onPipelineCreated(pipeline, mesh, ctx)
    }

    private fun setupUniforms(builder: Pipeline.Builder) {
        val descBuilder = DescriptorSetLayout.Builder()
        builder.descriptorSetLayouts += descBuilder

        program.uniformBuffers.filter { it.uniforms.isNotEmpty() }.forEach { kslUbo ->
            val ubo = UniformBuffer.Builder()
            descBuilder.descriptors += ubo

            ubo.name = kslUbo.name
            if (kslUbo.uniforms.values.any { u -> u.value in program.vertexStage.main.dependencies.keys }) {
                ubo.stages += ShaderStage.VERTEX_SHADER
            }
            if (kslUbo.uniforms.values.any { u -> u.value in program.fragmentStage.main.dependencies.keys }) {
                ubo.stages += ShaderStage.FRAGMENT_SHADER
            }

            kslUbo.uniforms.values.forEach { uniform ->
                // make sure to reuse the existing Uniform<*> object in case multiple pipeline instances are
                // created from this KslShader instance
                val createdUniform: Uniform<*> = uniforms[uniform.name] ?: when(val type = uniform.value.expressionType)  {
                    is KslTypeFloat1 -> { Uniform1f(uniform.name) }
                    is KslTypeFloat2 -> { Uniform2f(uniform.name) }
                    is KslTypeFloat3 -> { Uniform3f(uniform.name) }
                    is KslTypeFloat4 -> { Uniform4f(uniform.name) }

                    is KslTypeInt1 -> { Uniform1i(uniform.name) }
                    is KslTypeInt2 -> { Uniform2i(uniform.name) }
                    is KslTypeInt3 -> { Uniform3i(uniform.name) }
                    is KslTypeInt4 -> { Uniform4i(uniform.name) }

                    //is KslTypeMat2 -> { UniformMat2f(uniform.name) }
                    is KslTypeMat3 -> { UniformMat3f(uniform.name) }
                    is KslTypeMat4 -> { UniformMat4f(uniform.name) }

                    is KslTypeArray<*> -> {
                        when (type.elemType) {
                            is KslTypeFloat1 -> { Uniform1fv(uniform.name, uniform.arraySize) }
                            is KslTypeFloat2 -> { Uniform2fv(uniform.name, uniform.arraySize) }
                            is KslTypeFloat3 -> { Uniform3fv(uniform.name, uniform.arraySize) }
                            is KslTypeFloat4 -> { Uniform4fv(uniform.name, uniform.arraySize) }

                            is KslTypeInt1 -> { Uniform1iv(uniform.name, uniform.arraySize) }
                            is KslTypeInt2 -> { Uniform2iv(uniform.name, uniform.arraySize) }
                            is KslTypeInt3 -> { Uniform3iv(uniform.name, uniform.arraySize) }
                            is KslTypeInt4 -> { Uniform4iv(uniform.name, uniform.arraySize) }

                            is KslTypeMat3 -> { UniformMat3fv(uniform.name, uniform.arraySize) }
                            is KslTypeMat4 -> { UniformMat4fv(uniform.name, uniform.arraySize) }

                            else -> throw IllegalStateException("Unsupported uniform array type: ${type.elemType.typeName}")
                        }
                    }
                    else -> throw IllegalStateException("Unsupported uniform type: ${type.typeName}")
                }
                ubo.uniforms += { createdUniform }
            }
        }

        if (program.uniformSamplers.isNotEmpty()) {
            program.uniformSamplers.values.forEach { sampler ->
                val desc = when(val type = sampler.value.expressionType)  {
                    is KslTypeDepthSampler2d -> TextureSampler2d.Builder().apply { isDepthSampler = true }
                    is KslTypeDepthSamplerCube -> TextureSamplerCube.Builder().apply { isDepthSampler = true }
                    is KslTypeColorSampler1d -> TextureSampler1d.Builder()
                    is KslTypeColorSampler2d -> TextureSampler2d.Builder()
                    is KslTypeColorSampler3d -> TextureSampler3d.Builder()
                    is KslTypeColorSamplerCube -> TextureSamplerCube.Builder()

                    is KslTypeArray<*> -> {
                        when (type.elemType) {
                            is KslTypeDepthSampler2d -> TextureSampler2d.Builder().apply {
                                isDepthSampler = true
                                arraySize = sampler.arraySize
                            }
                            is KslTypeDepthSamplerCube -> TextureSamplerCube.Builder().apply {
                                isDepthSampler = true
                                arraySize = sampler.arraySize
                            }
                            is KslTypeColorSampler1d -> TextureSampler1d.Builder().apply { arraySize = sampler.arraySize }
                            is KslTypeColorSampler2d -> TextureSampler2d.Builder().apply { arraySize = sampler.arraySize }
                            is KslTypeColorSampler3d -> TextureSampler3d.Builder().apply { arraySize = sampler.arraySize }
                            is KslTypeColorSamplerCube -> TextureSamplerCube.Builder().apply { arraySize = sampler.arraySize }
                            else -> throw IllegalStateException("Unsupported sampler array type: ${type.elemType.typeName}")
                        }
                    }
                    else -> throw IllegalStateException("Unsupported sampler uniform type: ${type.typeName}")
                }
                desc.name = sampler.name
                if (sampler.value in program.vertexStage.main.dependencies.keys) {
                    desc.stages += ShaderStage.VERTEX_SHADER
                }
                if (sampler.value in program.fragmentStage.main.dependencies.keys) {
                    desc.stages += ShaderStage.FRAGMENT_SHADER
                }
                descBuilder.descriptors += desc
            }
        }

        // todo: push constants (is there such thing in webgpu? otherwise only relevant for vulkan...)
    }

    private fun setupAttributes(mesh: Mesh, builder: Pipeline.Builder) {
        var attribLocation = 0
        val verts = mesh.geometry
        val vertLayoutAttribs = mutableListOf<VertexLayout.VertexAttribute>()
        val vertLayoutAttribsI = mutableListOf<VertexLayout.VertexAttribute>()
        var iBinding = 0

        program.vertexStage.attributes.values.asSequence().filter { it.inputRate == KslInputRate.Vertex }.forEach { vertexAttrib ->
            val attrib = verts.attributeByteOffsets.keys.find { it.name == vertexAttrib.name }
                ?: throw NoSuchElementException("Mesh does not include required vertex attribute: ${vertexAttrib.name}")
            val off = verts.attributeByteOffsets[attrib]!!
            if (attrib.type.isInt) {
                vertLayoutAttribsI += VertexLayout.VertexAttribute(attribLocation, off, attrib)
            } else {
                vertLayoutAttribs += VertexLayout.VertexAttribute(attribLocation, off, attrib)
            }
            vertexAttrib.location = attribLocation
            attribLocation += attrib.props.nSlots
        }

        builder.vertexLayout.bindings += VertexLayout.Binding(
            iBinding++,
            InputRate.VERTEX,
            vertLayoutAttribs,
            verts.byteStrideF
        )
        if (vertLayoutAttribsI.isNotEmpty()) {
            builder.vertexLayout.bindings += VertexLayout.Binding(
                iBinding++,
                InputRate.VERTEX,
                vertLayoutAttribsI,
                verts.byteStrideI
            )
        }

        val instanceAttribs = program.vertexStage.attributes.values.filter { it.inputRate == KslInputRate.Instance }
        val insts = mesh.instances
        if (insts != null) {
            val instLayoutAttribs = mutableListOf<VertexLayout.VertexAttribute>()
            instanceAttribs.forEach { instanceAttrib ->
                val attrib = insts.attributeOffsets.keys.find { it.name == instanceAttrib.name }
                    ?: throw NoSuchElementException("Mesh does not include required instance attribute: ${instanceAttrib.name}")
                val off = insts.attributeOffsets[attrib]!!
                instLayoutAttribs += VertexLayout.VertexAttribute(attribLocation, off, attrib)
                instanceAttrib.location = attribLocation
                attribLocation += attrib.props.nSlots
            }
            builder.vertexLayout.bindings += VertexLayout.Binding(
                iBinding,
                InputRate.INSTANCE,
                instLayoutAttribs,
                insts.strideBytesF
            )
        } else if (instanceAttribs.isNotEmpty()) {
            throw IllegalStateException("Shader model requires instance attributes, but mesh doesn't provide any")
        }
    }

    class PipelineConfig {
        var blendMode = BlendMode.BLEND_MULTIPLY_ALPHA
        var cullMethod = CullMethod.CULL_BACK_FACES
        var depthTest = DepthCompareOp.LESS_EQUAL
        var isWriteDepth = true
        var lineWidth = 1f
    }

    protected interface ConnectUniformListener {
        fun connect()
    }

    protected fun uniform1f(uniformName: String?, defaultVal: Float? = null): UniformInput1f =
        UniformInput1f(uniformName, defaultVal ?: 0f).also { connectUniformListeners += it }
    protected fun uniform2f(uniformName: String?, defaultVal: Vec2f? = null): UniformInput2f =
        UniformInput2f(uniformName, defaultVal ?: Vec2f.ZERO).also { connectUniformListeners += it }
    protected fun uniform3f(uniformName: String?, defaultVal: Vec3f? = null): UniformInput3f =
        UniformInput3f(uniformName, defaultVal ?: Vec3f.ZERO).also { connectUniformListeners += it }
    protected fun uniform4f(uniformName: String?, defaultVal: Vec4f? = null): UniformInput4f =
        UniformInput4f(uniformName, defaultVal ?: Vec4f.ZERO).also { connectUniformListeners += it }

    protected fun uniformMat3f(uniformName: String?, defaultVal: Mat3f? = null): UniformInputMat3f =
        UniformInputMat3f(uniformName, defaultVal).also { connectUniformListeners += it }
    protected fun uniformMat4f(uniformName: String?, defaultVal: Mat4f? = null): UniformInputMat4f =
        UniformInputMat4f(uniformName, defaultVal).also { connectUniformListeners += it }

    protected fun texture1d(uniformName: String?, defaultVal: Texture1d? = null): UniformInputTexture1d =
        UniformInputTexture1d(uniformName, defaultVal).also { connectUniformListeners += it }
    protected fun texture2d(uniformName: String?, defaultVal: Texture2d? = null): UniformInputTexture2d =
        UniformInputTexture2d(uniformName, defaultVal).also { connectUniformListeners += it }
    protected fun texture3d(uniformName: String?, defaultVal: Texture3d? = null): UniformInputTexture3d =
        UniformInputTexture3d(uniformName, defaultVal).also { connectUniformListeners += it }
    protected fun textureCube(uniformName: String?, defaultVal: TextureCube? = null): UniformInputTextureCube =
        UniformInputTextureCube(uniformName, defaultVal).also { connectUniformListeners += it }

    protected inner class UniformInput1f(val uniformName: String?, defaultVal: Float) : ConnectUniformListener {
        private var uniform: Uniform1f? = null
        private var buffer = defaultVal
        override fun connect() { uniform = (uniforms[uniformName] as? Uniform1f)?.apply { value = buffer } }
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Float = uniform?.value ?: buffer
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) {
            uniform?.let { it.value = value } ?: run { buffer = value }
        }
    }

    protected inner class UniformInput2f(val uniformName: String?, defaultVal: Vec2f) : ConnectUniformListener {
        private var uniform: Uniform2f? = null
        private val buffer = MutableVec2f(defaultVal)
        override fun connect() { uniform = (uniforms[uniformName] as? Uniform2f)?.apply { value.set(buffer) } }
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Vec2f = uniform?.value ?: buffer
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Vec2f) = (uniform?.value ?: buffer).set(value)
    }

    protected inner class UniformInput3f(val uniformName: String?, defaultVal: Vec3f) : ConnectUniformListener {
        private var uniform: Uniform3f? = null
        private val buffer = MutableVec3f(defaultVal)
        override fun connect() { uniform = (uniforms[uniformName] as? Uniform3f)?.apply { value.set(buffer) } }
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Vec3f = uniform?.value ?: buffer
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Vec3f) = (uniform?.value ?: buffer).set(value)
    }

    protected inner class UniformInput4f(val uniformName: String?, defaultVal: Vec4f) : ConnectUniformListener {
        private var uniform: Uniform4f? = null
        private val buffer = MutableVec4f(defaultVal)
        override fun connect() { uniform = (uniforms[uniformName] as? Uniform4f)?.apply { value.set(buffer) } }
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Vec4f = uniform?.value ?: buffer
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Vec4f) = (uniform?.value ?: buffer).set(value)
    }

    protected inner class UniformInputMat3f(val uniformName: String?, defaultVal: Mat3f?) : ConnectUniformListener {
        var uniform: UniformMat3f? = null
        private val buffer = Mat3f().apply { defaultVal?.let { set(it) } }
        override fun connect() { uniform = (uniforms[uniformName] as? UniformMat3f)?.apply { value.set(buffer) } }
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Mat3f = uniform?.value ?: buffer
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Mat3f) = (uniform?.value ?: buffer).set(value)
    }

    protected inner class UniformInputMat4f(val uniformName: String?, defaultVal: Mat4f?) : ConnectUniformListener {
        var uniform: UniformMat4f? = null
        private val buffer = Mat4f().apply { defaultVal?.let { set(it) } }
        override fun connect() { uniform = (uniforms[uniformName] as? UniformMat4f)?.apply { value.set(buffer) } }
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Mat4f = uniform?.value ?: buffer
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Mat4f) = (uniform?.value ?: buffer).set(value)
    }

    protected inner class UniformInputTexture1d(val uniformName: String?, defaultVal: Texture1d?) : ConnectUniformListener {
        var uniform: TextureSampler1d? = null
        private var buffer: Texture1d? = defaultVal
        override fun connect() { uniform = texSamplers1d[uniformName]?.apply { texture = buffer } }
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Texture1d? = uniform?.texture ?: buffer
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Texture1d?) {
            uniform?.let { it.texture = value } ?: run { buffer = value }
        }
    }

    protected inner class UniformInputTexture2d(val uniformName: String?, defaultVal: Texture2d?) : ConnectUniformListener {
        var uniform: TextureSampler2d? = null
        private var buffer: Texture2d? = defaultVal
        override fun connect() { uniform = texSamplers2d[uniformName]?.apply { texture = buffer } }
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Texture2d? = uniform?.texture ?: buffer
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Texture2d?) {
            uniform?.let { it.texture = value } ?: run { buffer = value }
        }
    }

    protected inner class UniformInputTexture3d(val uniformName: String?, defaultVal: Texture3d?) : ConnectUniformListener {
        var uniform: TextureSampler3d? = null
        private var buffer: Texture3d? = defaultVal
        override fun connect() { uniform = texSamplers3d[uniformName]?.apply { texture = buffer } }
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Texture3d? = uniform?.texture ?: buffer
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Texture3d?) {
            uniform?.let { it.texture = value } ?: run { buffer = value }
        }
    }

    protected inner class UniformInputTextureCube(val uniformName: String?, defaultVal: TextureCube?) : ConnectUniformListener {
        var uniform: TextureSamplerCube? = null
        private var buffer: TextureCube? = defaultVal
        override fun connect() { uniform = texSamplersCube[uniformName]?.apply { texture = buffer } }
        operator fun getValue(thisRef: Any?, property: KProperty<*>): TextureCube? = uniform?.texture ?: buffer
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: TextureCube?) {
            uniform?.let { it.texture = value } ?: run { buffer = value }
        }
    }
}