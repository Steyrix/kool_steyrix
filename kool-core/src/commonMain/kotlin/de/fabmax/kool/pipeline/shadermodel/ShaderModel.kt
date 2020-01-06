package de.fabmax.kool.pipeline.shadermodel

import de.fabmax.kool.KoolContext
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.scene.Mesh
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class ShaderModel(val modelInfo: String = "") {
    val stages = mutableMapOf(
            ShaderStage.VERTEX_SHADER to VertexShaderGraph(),
            ShaderStage.FRAGMENT_SHADER to FragmentShaderGraph()
    )

    val vertexStage: VertexShaderGraph
        get() = stages[ShaderStage.VERTEX_SHADER] as VertexShaderGraph
    val fragmentStage: FragmentShaderGraph
        get() = stages[ShaderStage.FRAGMENT_SHADER] as FragmentShaderGraph

    fun <T: ShaderNode> findNode(name: String, stage: ShaderStage = ShaderStage.ALL): T? {
        stages.values.forEach {
            if (it.stage.mask and stage.mask != 0) {
                val node = it.findNode<T>(name)
                if (node != null) {
                    return node
                }
            }
        }
        return null
    }

    fun setup(mesh: Mesh, buildCtx: Pipeline.BuildContext, ctx: KoolContext) {
        stages.values.forEach { it.setup() }

        setupAttributes(mesh, buildCtx)
        val descBuilder = DescriptorSetLayout.Builder()
        val pushBuilder = PushConstantRange.Builder()
        stages.values.forEach { stage ->
            descBuilder.descriptors += stage.descriptorSet.descriptors
            pushBuilder.pushConstants += stage.pushConstants.pushConstants
            pushBuilder.stages += stage.pushConstants.stages
        }
        if (descBuilder.descriptors.isNotEmpty()) {
            buildCtx.descriptorSetLayouts += descBuilder
        }
        if (pushBuilder.pushConstants.isNotEmpty()) {
            buildCtx.pushConstantRanges += pushBuilder
        }
    }

    private fun setupAttributes(mesh: Mesh, buildCtx: Pipeline.BuildContext) {
        val vertLayoutAttribs = mutableListOf<VertexLayout.Attribute>()
        val verts = mesh.meshData.vertexList

        vertexStage.requiredVertexAttributes.forEachIndexed { iAttrib, attrib ->
            if (!mesh.meshData.vertexAttributes.contains(attrib)) {
                throw NoSuchElementException("Mesh does not include required vertex attribute: ${attrib.name}")
            }
            val off = verts.attributeOffsets[attrib] ?: throw NoSuchElementException()
            vertLayoutAttribs += VertexLayout.Attribute(iAttrib, off, attrib.type, attrib.name)
        }

        if (buildCtx.vertexLayout.bindings.isNotEmpty()) {
            TODO("multiple attribute bindings are not yet implemented: attribute location must be changed")
        }

        buildCtx.vertexLayout.bindings += VertexLayout.Binding(0, InputRate.VERTEX, vertLayoutAttribs, verts.strideBytesF)
    }

    abstract inner class StageBuilder(val stage: ShaderGraph) {
        fun <T: ShaderNode> addNode(node: T): T {
            stage.addNode(node)
            return node
        }

        fun normalizeNode(input: ShaderNodeIoVar? = null): NormalizeNode {
            val nrmNode = addNode(NormalizeNode(stage))
            input?.let { nrmNode.input = input }
            return nrmNode
        }

        fun hdrToLdrNode(inputColor: ShaderNodeIoVar? = null): HdrToLdrNode {
            val hdr2ldr = addNode(HdrToLdrNode(stage))
            inputColor?.let { hdr2ldr.inColor = it }
            return hdr2ldr
        }

        fun premultiplyColorNode(inColor: ShaderNodeIoVar? = null): PremultiplyColorNode {
            val preMult = addNode(PremultiplyColorNode(stage))
            inColor?.let { preMult.inColor = it }
            return preMult
        }

        fun pushConstantNode1f(name: String) = addNode(PushConstantNode1f(Uniform1f(name), stage))
        fun pushConstantNode2f(name: String) = addNode(PushConstantNode2f(Uniform2f(name), stage))
        fun pushConstantNode3f(name: String) = addNode(PushConstantNode3f(Uniform3f(name), stage))
        fun pushConstantNode4f(name: String) = addNode(PushConstantNode4f(Uniform4f(name), stage))
        fun pushConstantNodeColor(name: String) = addNode(PushConstantNodeColor(UniformColor(name), stage))

        fun pushConstantNode1f(u: Uniform1f) = addNode(PushConstantNode1f(u, stage))
        fun pushConstantNode2f(u: Uniform2f) = addNode(PushConstantNode2f(u, stage))
        fun pushConstantNode3f(u: Uniform3f) = addNode(PushConstantNode3f(u, stage))
        fun pushConstantNode4f(u: Uniform4f) = addNode(PushConstantNode4f(u, stage))
        fun pushConstantNodeColor(u: UniformColor) = addNode(PushConstantNodeColor(u, stage))

        fun textureNode(texName: String) = addNode(TextureNode(stage, texName))

        fun textureSamplerNode(texNode: TextureNode, texCoords: ShaderNodeIoVar? = null, premultiply: Boolean = true): TextureSamplerNode {
            val texSampler = addNode(TextureSamplerNode(texNode, stage, premultiply))
            texCoords?.let { texSampler.inTexCoord = it }
            return texSampler
        }

        fun cubeMapNode(texName: String) = addNode(CubeMapNode(stage, texName))

        fun cubeMapSamplerNode(texNode: CubeMapNode, texCoords: ShaderNodeIoVar? = null, premultiply: Boolean = true): CubeMapSamplerNode {
            val texSampler = addNode(CubeMapSamplerNode(texNode, stage, premultiply))
            texCoords?.let { texSampler.inTexCoord = it }
            return texSampler
        }
    }

    inner class VertexStageBuilder : StageBuilder(vertexStage) {
        var positionOutput: ShaderNodeIoVar
            get() = vertexStage.positionOutput
            set(value) { vertexStage.positionOutput = value }

        fun attrColors() = attributeNode(Attribute.COLORS)
        fun attrNormals() = attributeNode(Attribute.NORMALS)
        fun attrPositions() = attributeNode(Attribute.POSITIONS)
        fun attrTangents() = attributeNode(Attribute.TANGENTS)
        fun attrTexCoords() = attributeNode(Attribute.TEXTURE_COORDS)
        fun attributeNode(attribute: Attribute) = addNode(AttributeNode(attribute, stage))

        fun stageInterfaceNode(name: String, input: ShaderNodeIoVar?): StageInterfaceNode {
            val ifNode = StageInterfaceNode(name, vertexStage, fragmentStage)
            input?.let { ifNode.input = it }
            addNode(ifNode.vertexNode)
            fragmentStage.addNode(ifNode.fragmentNode)
            return ifNode
        }

        fun mvpNode() = addNode(UniformBufferMvp(stage))

        fun premultipliedMvpNode() = addNode(UniformBufferPremultipliedMvp(stage))

        fun simpleVertexPositionNode() = vertexPositionNode(attrPositions().output, premultipliedMvpNode().outMvpMat)

        fun transformNode(input: ShaderNodeIoVar? = null, inMat: ShaderNodeIoVar? = null,
                          w: Float = 1f, invert: Boolean = false): TransformNode {
            val tf = addNode(TransformNode(stage, w, invert))
            input?.let { tf.input = it }
            inMat?.let { tf.inMat = it }
            return tf
        }

        fun vertexPositionNode(inPosition: ShaderNodeIoVar? = null, inMvp: ShaderNodeIoVar? = null): VertexPosTransformNode {
            val pos = addNode(VertexPosTransformNode(stage))
            inPosition?.let { pos.inPosition = it }
            inMvp?.let { pos.inMvp = it }
            return pos
        }
    }

    inner class FragmentStageBuilder : StageBuilder(fragmentStage) {
        var colorOutput: ShaderNodeIoVar
            get() = fragmentStage.colorOutput
            set(value) { fragmentStage.colorOutput = value }

        fun defaultLightNode(maxLights: Int = 4) = addNode(LightNode(stage, maxLights))

        fun unlitMaterialNode(albedo: ShaderNodeIoVar? = null): UnlitMaterialNode {
            val mat = addNode(UnlitMaterialNode(stage))
            albedo?.let { mat.inAlbedo = it }
            return mat
        }

        /**
         * Phong shader with multiple light sources. The shader assumes normals, fragment and cam positions are in
         * world space.
         */
        fun phongMaterialNode(albedo: ShaderNodeIoVar? = null, normal: ShaderNodeIoVar? = null,
                              fragPos: ShaderNodeIoVar? = null, camPos: ShaderNodeIoVar? = null, lightNode: LightNode): PhongMaterialNode {
            val mat = addNode(PhongMaterialNode(lightNode, stage))
            albedo?.let { mat.inAlbedo = it }
            normal?.let { mat.inNormal = it }
            camPos?.let { mat.inCamPos = it }
            fragPos?.let { mat.inFragPos = it }
            return mat
        }

        fun pbrMaterialNode(albedo: ShaderNodeIoVar? = null, normal: ShaderNodeIoVar? = null,
                              fragPos: ShaderNodeIoVar? = null, camPos: ShaderNodeIoVar? = null, lightNode: LightNode): PbrMaterialNode {
            val mat = addNode(PbrMaterialNode(lightNode, stage))
            albedo?.let { mat.inAlbedo = it }
            normal?.let { mat.inNormal = it }
            camPos?.let { mat.inCamPos = it }
            fragPos?.let { mat.inFragPos = it }
            return mat
        }
    }
}

inline fun ShaderModel.vertexStage(block: ShaderModel.VertexStageBuilder.() -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    VertexStageBuilder().block()
}

inline fun ShaderModel.fragmentStage(block: ShaderModel.FragmentStageBuilder.() -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    FragmentStageBuilder().block()
}
