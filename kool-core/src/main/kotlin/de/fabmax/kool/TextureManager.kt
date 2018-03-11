package de.fabmax.kool

import de.fabmax.kool.gl.*

/**
 * @author fabmax
 */
class TextureManager internal constructor() : SharedResManager<TextureProps, TextureResource>() {

    var maxTextureLoadsPerFrame = 5

    private var activeTexUnit = 0
    private var boundTextures = Array<TextureResource?>(16, { null })
    private val loadingTextures: MutableMap<String, TextureData> = mutableMapOf()

    private var allowedTexLoads = maxTextureLoadsPerFrame

    fun onNewFrame(ctx: RenderContext) {
        allowedTexLoads = maxTextureLoadsPerFrame

        if (boundTextures.size != glCapabilities.maxTexUnits) {
            boundTextures = Array(glCapabilities.maxTexUnits, { null })
        }
    }

    fun bindTexture(texture: Texture, ctx: RenderContext): Int {
        if (!texture.isValid) {
            nextTexUnit()
            texture.onCreate(ctx)
        }
        val texRes = texture.res ?: throw KoolException("TextureResource is null although it was created")

        if (texRes.texUnit < 0) {
            nextTexUnit()
            bindToActiveTexUnit(texture.res)
        }
        // upload texture data to GPU if that hasn't happened yet
        if (!texRes.isLoaded) {
            loadTexture(texture, ctx)
        }

        return texRes.texUnit
    }

    internal fun createTexture(props: TextureProps, ctx: RenderContext): TextureResource {
        return addReference(props, ctx)
    }

    internal fun deleteTexture(texture: Texture, ctx: RenderContext) {
        val res = texture.res
        if (res != null) {
            removeReference(texture.props, ctx)
        }
    }

    private fun nextTexUnit() {
        activateTexUnit((activeTexUnit + 1) % boundTextures.size)
    }

    private fun activateTexUnit(unit: Int) {
        activeTexUnit = unit
        glActiveTexture(GL_TEXTURE0 + unit)
    }

    private fun bindToActiveTexUnit(texRes: TextureResource?) {
        boundTextures[activeTexUnit]?.texUnit = -1

        glBindTexture(GL_TEXTURE_2D, texRes)
        texRes?.texUnit = activeTexUnit
        boundTextures[activeTexUnit] = texRes
    }

    private fun loadTexture(texture: Texture, ctx: RenderContext) {
        val res = texture.res ?: throw KoolException("Can't load a texture that wasn't created")

        // check if texture is already loading
        var data = loadingTextures[texture.props.id]
        if (data == null) {
            // initiate loading of texture data
            data = texture.generator(texture)
            loadingTextures[texture.props.id] = data
        }
        // texture data is available (depending on the texture source that might not be the case immediately)
        if (data.isAvailable && allowedTexLoads > 0) {
            if (res.texUnit != activeTexUnit) {
                activateTexUnit(texture.res!!.texUnit)
            }
            texture.loadData(data, ctx)
            loadingTextures.remove(texture.props.id)
            allowedTexLoads--
        }
    }

    override fun createResource(key: TextureProps, ctx: RenderContext): TextureResource {
        val texRes = TextureResource.create(GL_TEXTURE_2D, key, ctx)
        bindToActiveTexUnit(texRes)
        return texRes
    }

    override fun deleteResource(key: TextureProps, res: TextureResource, ctx: RenderContext) {
        res.delete(ctx)
    }
}
