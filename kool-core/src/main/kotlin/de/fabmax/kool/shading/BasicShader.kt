package de.fabmax.kool.shading

import de.fabmax.kool.Texture2d
import de.fabmax.kool.platform.RenderContext
import de.fabmax.kool.platform.Platform
import de.fabmax.kool.platform.ShaderGenerator
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MutableVec3f
import de.fabmax.kool.util.MutableVec4f


fun basicShader(propsInit: ShaderProps.() -> Unit): BasicShader {
    return BasicShader(ShaderProps(propsInit))
}

/**
 * Generic simple shader generated by [ShaderProps]
 */
open class BasicShader(props: ShaderProps, private val generator: ShaderGenerator = Platform.createDefaultShaderGenerator()) :
        Shader(generator.generate(props)) {

    protected var lightColor: MutableVec3f
        get() = generator.uniformLightColor.value
        set(value) { generator.uniformLightColor.value.set(value) }
    protected var lightDirection: MutableVec3f
        get() = generator.uniformLightDirection.value
        set(value) { generator.uniformLightDirection.value.set(value) }
    protected var cameraPosition: MutableVec3f
        get() = generator.uniformCameraPosition.value
        set(value) { generator.uniformCameraPosition.value.set(value) }

    var shininess: Float
        get() = generator.uniformShininess.value
        set(value) { generator.uniformShininess.value = value }
    var specularIntensity: Float
        get() = generator.uniformSpecularIntensity.value
        set(value) { generator.uniformSpecularIntensity.value = value }

    var staticColor: MutableVec4f
        get() = generator.uniformStaticColor.value
        set(value) { generator.uniformStaticColor.value.set(value) }
    var texture: Texture2d?
        get() = generator.uniformTexture.value
        set(value) { generator.uniformTexture.value = value }

    var alpha: Float
        get() = generator.uniformAlpha.value
        set(value) { generator.uniformAlpha.value = value }

    var saturation: Float
        get() = generator.uniformSaturation.value
        set(value) { generator.uniformSaturation.value = value }

    var fogColor: MutableVec4f
        get() = generator.uniformFogColor.value
        set(value) { generator.uniformFogColor.value.set(value) }
    var fogRange: Float
        get() = generator.uniformFogRange.value
        set(value) { generator.uniformFogRange.value = value }

    init {
        // set meaningful uniform default values
        shininess = 20.0f
        specularIntensity = 0.75f
        staticColor.set(Color.RED)
        alpha = 1.0f
        saturation = 1.0f
        fogRange = 250.0f
        fogColor.set(Color.LIGHT_GRAY)
    }

    override fun onLoad(ctx: RenderContext) {
        super.onLoad(ctx)
        generator.onLoad(this)
    }

    override fun onBind(ctx: RenderContext) {
        onMatrixUpdate(ctx)

        // fixme: get camera position
        cameraPosition.set(0f, 0f, 0f)
        generator.uniformCameraPosition.bind(ctx)

        val light = ctx.scene.light
        lightDirection.set(light.direction)
        generator.uniformLightDirection.bind(ctx)
        lightColor.set(light.color.r, light.color.g, light.color.b)
        generator.uniformLightColor.bind(ctx)

        // fixme: if (isGlobalFog) fogColor.set(global)
        generator.uniformFogColor.bind(ctx)
        // fixme: if (isGlobalFog) fogRange = global
        generator.uniformFogRange.bind(ctx)

        // fixme: if (isGlobalSaturation) saturation = globalSaturation
        generator.uniformSaturation.bind(ctx)

        generator.uniformAlpha.bind(ctx)
        generator.uniformShininess.bind(ctx)
        generator.uniformSpecularIntensity.bind(ctx)
        generator.uniformStaticColor.bind(ctx)
        generator.uniformTexture.bind(ctx)
    }

    override fun onMatrixUpdate(ctx: RenderContext) {
        // pass current transformation matrices to shader
        generator.uniformMvpMatrix.value = ctx.mvpState.mvpMatrixBuffer
        generator.uniformMvpMatrix.bind(ctx)

        generator.uniformViewMatrix.value = ctx.mvpState.viewMatrixBuffer
        generator.uniformViewMatrix.bind(ctx)

        generator.uniformModelMatrix.value = ctx.mvpState.modelMatrixBuffer
        generator.uniformModelMatrix.bind(ctx)
    }

}
