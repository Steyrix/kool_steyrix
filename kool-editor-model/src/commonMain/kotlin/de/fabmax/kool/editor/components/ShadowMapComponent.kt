package de.fabmax.kool.editor.components

import de.fabmax.kool.editor.api.AppState
import de.fabmax.kool.editor.api.GameEntity
import de.fabmax.kool.editor.api.sceneComponent
import de.fabmax.kool.editor.data.ShadowMapComponentData
import de.fabmax.kool.editor.data.ShadowMapInfo
import de.fabmax.kool.editor.data.ShadowMapTypeData
import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.scene.Light
import de.fabmax.kool.util.*

class ShadowMapComponent(
    gameEntity: GameEntity,
    componentData: ShadowMapComponentData = ShadowMapComponentData(ShadowMapTypeData.Single(ShadowMapInfo()))
) : GameEntityDataComponent<ShadowMapComponentData>(gameEntity, componentData) {
    val shadowMapState = mutableStateOf(componentData.shadowMap).onChange {
        if (AppState.isEditMode) { componentData.shadowMap = it }
        updateShadowMap(it, componentData.clipNear, componentData.clipFar)
    }

    val clipNear = mutableStateOf(componentData.clipNear).onChange {
        if (AppState.isEditMode) { componentData.clipNear = it }
        updateShadowMap(componentData.shadowMap, it, componentData.clipFar)
    }

    val clipFar = mutableStateOf(componentData.clipNear).onChange {
        if (AppState.isEditMode) { componentData.clipFar = it }
        updateShadowMap(componentData.shadowMap, componentData.clipNear, it)
    }

    private var shadowMap: ShadowMap? = null

    init {
        dependsOn(DiscreteLightComponent::class)
    }

    override suspend fun applyComponent() {
        super.applyComponent()
        shadowMapState.set(componentData.shadowMap)
        updateShadowMap()
    }

    override fun destroyComponent() {
        disposeShadowMap()
        UpdateShadowMapsComponent.updateShadowMaps(sceneEntity)
        super.destroyComponent()
    }

    fun updateLight(light: Light) {
        val current = shadowMap?.light
        if (current != null && current::class == light::class) {
            shadowMap?.light = light
        } else {
            updateShadowMap()
        }
    }

    private fun updateShadowMap() {
        updateShadowMap(componentData.shadowMap, componentData.clipNear, componentData.clipFar)
    }

    private fun updateShadowMap(shadowMapInfo: ShadowMapTypeData, clipNear: Float, clipFar: Float) {
        logD { "Update shadow map: ${shadowMapInfo::class.simpleName}, near: $clipNear, far: $clipFar" }

        val light = gameEntity.getComponent<DiscreteLightComponent>()?.drawNode
        if (light == null) {
            logE { "Unable to get DiscreteLightComponent of sceneNode ${gameEntity.name}" }
            return
        }
        if (light is Light.Point) {
            logE { "Point light shadow maps are not yet supported" }
            return
        }

        // dispose old shadow map
        disposeShadowMap()

        // create new shadow map
        shadowMap = when (shadowMapInfo) {
            is ShadowMapTypeData.Single -> {
                SimpleShadowMap(sceneComponent.drawNode, light, mapSize = shadowMapInfo.mapInfo.mapSize).apply {
                    this.clipNear = clipNear
                    this.clipFar = clipFar
                }
            }
            is ShadowMapTypeData.Cascaded -> {
                CascadedShadowMap(
                    sceneComponent.drawNode,
                    light,
                    clipFar,
                    shadowMapInfo.mapInfos.size,
                    mapSizes = shadowMapInfo.mapInfos.map { it.mapSize }
                ).apply {
                    shadowMapInfo.mapInfos.forEachIndexed { i, info ->
                        mapRanges[i].near = info.rangeNear
                        mapRanges[i].far = info.rangeFar
                    }
                }
            }
        }.also {
            sceneComponent.shaderData.shadowMaps += it
            UpdateShadowMapsComponent.updateShadowMaps(sceneEntity)
        }
    }

    private fun disposeShadowMap() {
        val scene = sceneComponent.drawNode
        shadowMap?.let {
            sceneComponent.shaderData.shadowMaps -= it
            when (it) {
                is SimpleShadowMap -> {
                    scene.removeOffscreenPass(it)
                    it.release()
                }
                is CascadedShadowMap -> {
                    it.subMaps.forEach { pass ->
                        scene.removeOffscreenPass(pass)
                        pass.release()
                    }
                }
            }
        }
    }
}

interface UpdateShadowMapsComponent {
    fun updateShadowMaps(shadowMaps: List<ShadowMap>)

    companion object {
        fun updateShadowMaps(sceneEntity: GameEntity) {
            sceneEntity.scene.getAllComponents<UpdateShadowMapsComponent>().forEach {
                it.updateShadowMaps(sceneEntity.sceneComponent.shaderData.shadowMaps)
            }
        }
    }
}
