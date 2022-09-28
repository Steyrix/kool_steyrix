package de.fabmax.kool.demo.physics.terrain

import de.fabmax.kool.AssetManager
import de.fabmax.kool.InputManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.demo.*
import de.fabmax.kool.demo.menu.DemoMenu
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.toDeg
import de.fabmax.kool.modules.gltf.loadGltfModel
import de.fabmax.kool.modules.ksl.*
import de.fabmax.kool.modules.ksl.blocks.ColorSpaceConversion
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.physics.Physics
import de.fabmax.kool.physics.util.CharacterTrackingCamRig
import de.fabmax.kool.pipeline.AddressMode
import de.fabmax.kool.pipeline.Attribute
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.pipeline.TextureProps
import de.fabmax.kool.pipeline.ao.AoPipeline
import de.fabmax.kool.scene.Mesh
import de.fabmax.kool.scene.MeshInstanceList
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.colorMesh
import de.fabmax.kool.util.*
import kotlin.math.atan2

class TerrainDemo : DemoScene("Terrain Demo") {

    private lateinit var colorMap: Texture2d
    private lateinit var normalMap: Texture2d
    private lateinit var grassColor: Texture2d
    private lateinit var oceanBump: Texture2d
    private lateinit var shadowMap: ShadowMap
    private lateinit var ssao: AoPipeline.ForwardAoPipeline
    private lateinit var oceanFloorPass: OceanFloorRenderPass
    private lateinit var sky: Sky

    private lateinit var wind: Wind
    private lateinit var terrain: Terrain
    private lateinit var trees: Trees
    private lateinit var grass: Grass
    private lateinit var camLocalGrass: CamLocalGrass
    private lateinit var ocean: Ocean

    private lateinit var playerModel: PlayerModel
    private lateinit var camRig: CharacterTrackingCamRig
    private lateinit var terrainTiles: TerrainTiles
    private lateinit var physicsObjects: PhysicsObjects

    private var boxMesh: Mesh? = null
    private var bridgeMesh: Mesh? = null

    private lateinit var escKeyListener: InputManager.KeyEventListener

    private val isSsao = mutableStateOf(true).onChange { ssao.isEnabled = it }
    private val isPlayerPbr = mutableStateOf(true).onChange { updatePlayerShader(it) }
    private val isGroundPbr = mutableStateOf(true).onChange {
        updateTerrainShader(it)
        updateOceanShader(it)
        updateBridgeShader(it)
    }
    private val isBoxesPbr = mutableStateOf(true).onChange { updateBoxShader(it) }
    private val isVegetationPbr = mutableStateOf(false).onChange {
        updateGrassShader(it)
        updateTreeShader(it)
    }

    private val isCursorLocked = mutableStateOf(true).onChange { camRig.isCursorLocked = it }
    private val windSpeed = mutableStateOf(2.5f).onChange {
        wind.speed.set(4f * it, 0.2f * it, 2.7f * it)
    }
    private val windStrength = mutableStateOf(1f).onChange {
        wind.offsetStrength.w = it
    }
    private val windScale = mutableStateOf(100f).onChange {
        wind.scale = it
    }
    private val isGrassEnabled = mutableStateOf(true).onChange { grass.grassQuads.isVisible = it }
    private val isCamLocalGrassEnabled = mutableStateOf(true).onChange { camLocalGrass.grassQuads.isVisible = it }
    private val isGrassShadows = mutableStateOf(true).onChange {
        grass.setIsCastingShadow(it)
        camLocalGrass.setIsCastingShadow(it)
    }

        override suspend fun AssetManager.loadResources(ctx: KoolContext) {
        showLoadText("Loading height map...")
        val heightMap = HeightMap.fromRawData(loadAsset("${DemoLoader.heightMapPath}/terrain_ocean.raw")!!, 200f, heightOffset = -50f)
        // more or less the same, but falls back to 8-bit height-resolution in javascript
        //heightMap = HeightMap.fromTextureData2d(loadTextureData2d("${Demo.heightMapPath}/terrain.png", TexFormat.R_F16), 200f)

        showLoadText("Loading textures...")
        colorMap = loadAndPrepareTexture("${DemoLoader.materialPath}/tile_flat/tiles_flat_fine.png")
        normalMap = loadAndPrepareTexture("${DemoLoader.materialPath}/tile_flat/tiles_flat_fine_normal.png")
        oceanBump = loadAndPrepareTexture("${DemoLoader.materialPath}/ocean-bump-1k.jpg")
        val moonTex = loadAndPrepareTexture("${DemoLoader.materialPath}/moon-blueish.png")

        val grassProps = TextureProps(
            addressModeU = AddressMode.CLAMP_TO_EDGE,
            addressModeV = AddressMode.CLAMP_TO_EDGE
        )
        grassColor = loadAndPrepareTexture("${DemoLoader.materialPath}/grass_64.png", grassProps)

        showLoadText("Generating wind density texture...")
        wind = Wind()
        wind.offsetStrength.w = windStrength.value
        wind.scale = windScale.value

        sky = Sky(mainScene, moonTex).apply { generateMaps(this@TerrainDemo, loadingScreen!!, ctx) }
        showLoadText("Creating terrain...")
        Physics.awaitLoaded()
        terrain = Terrain(heightMap)
        terrainTiles = TerrainTiles(terrain, sky)
        showLoadText("Creating ocean...")
        oceanFloorPass = OceanFloorRenderPass(mainScene, terrainTiles)
        ocean = Ocean(terrainTiles, mainScene.camera, wind, sky)
        showLoadText("Creating trees...")
        trees = Trees(terrain, 150, wind, sky)
        showLoadText("Creating grass (1/2, may take a bit)...")
        grass = Grass(terrain, wind, sky)
        showLoadText("Creating grass (2/2, may take a bit)...")
        camLocalGrass = CamLocalGrass(mainScene.camera, terrain, wind, sky)

        showLoadText("Creating physics...")
        physicsObjects = PhysicsObjects(mainScene, terrain, trees, ctx)
        boxMesh = if (physicsObjects.boxes.isNotEmpty()) makeBoxMesh() else null
        bridgeMesh = makeBridgeMesh()

        showLoadText("Loading player model...")
        val playerGltf = loadGltfModel("${DemoLoader.modelPath}/player.glb") ?: throw IllegalStateException("Failed loading model")
        playerModel = PlayerModel(playerGltf, physicsObjects.playerController)

        escKeyListener = ctx.inputMgr.registerKeyListener(InputManager.KEY_ESC, "Exit cursor lock") {
            isCursorLocked.set(false)
            ctx.inputMgr.cursorMode = InputManager.CursorMode.NORMAL
        }
    }

    override fun dispose(ctx: KoolContext) {
        colorMap.dispose()
        normalMap.dispose()
        physicsObjects.release(ctx)

        ctx.inputMgr.removeKeyListener(escKeyListener)
        ctx.inputMgr.cursorMode = InputManager.CursorMode.NORMAL
    }

    override fun createMenu(menu: DemoMenu, ctx: KoolContext) = menuSurface {
        Button(if (isCursorLocked.use()) "ESC to unlock cursor" else "Lock cursor") {
            modifier
                .alignX(AlignmentX.Center)
                .width(Grow.Std)
                .margin(horizontal = 16.dp, vertical = 24.dp)
                .onClick { isCursorLocked.set(true) }
            if (isCursorLocked.use()) {
                modifier.colors(buttonColor = MdColor.RED)
            }
        }
        MenuRow {
            Text("[WASD / cursor keys]") { labelStyle(Grow.Std) }
            Text("move") { labelStyle() }
        }
        MenuRow {
            Text("[Shift]") { labelStyle(Grow.Std) }
            Text("walk") { labelStyle() }
        }
        MenuRow {
            Text("[Space]") { labelStyle(Grow.Std) }
            Text("jump") { labelStyle() }
        }
//        toggleButton("Draw Debug Info", playerModel.isDrawShapeOutline) {
//            playerModel.isDrawShapeOutline = isEnabled
//            physicsObjects.debugLines.isVisible = isEnabled
//        }

        Text("Wind") { sectionTitleStyle() }
        val lblSize = UiSizes.baseElemSize * 1.5f
        val txtSize = UiSizes.baseElemSize * 1.1f
        MenuRow {
            Text("Speed") { labelStyle(lblSize) }
            MenuSlider(windSpeed.use(), 0.1f, 20f, txtWidth = txtSize) { windSpeed.set(it) }
        }
        MenuRow {
            Text("Strength") { labelStyle(lblSize) }
            MenuSlider(windStrength.use(), 0f, 2f, txtWidth = txtSize) { windStrength.set(it) }
        }
        MenuRow {
            Text("Scale") { labelStyle(lblSize) }
            MenuSlider(windScale.use(), 10f, 500f, txtWidth = txtSize) { windScale.set(it) }
        }

        Text("Grass") { sectionTitleStyle() }
        MenuRow { LabeledSwitch("Enabled", isGrassEnabled) }
        MenuRow { LabeledSwitch("Dense grass", isCamLocalGrassEnabled) }
        MenuRow { LabeledSwitch("Shadow casting", isGrassShadows) }

        Text("Shading") { sectionTitleStyle() }
        MenuRow { LabeledSwitch("Ambient occlusion", isSsao) }
        MenuRow { LabeledSwitch("Player PBR shading", isPlayerPbr) }
        MenuRow { LabeledSwitch("Ground PBR shading", isGroundPbr) }
        MenuRow { LabeledSwitch("Boxes PBR shading", isBoxesPbr) }
        MenuRow { LabeledSwitch("Vegetation PBR shading", isVegetationPbr) }

        // crosshair
        surface.popup().apply {
            modifier.width(24.dp).height(24.dp).align(AlignmentX.Center, AlignmentY.Center)
            Box { modifier.width(10.dp).height(2.dp).align(AlignmentX.Start, AlignmentY.Center).backgroundColor(Color.WHITE) }
            Box { modifier.width(10.dp).height(2.dp).align(AlignmentX.End, AlignmentY.Center).backgroundColor(Color.WHITE) }
            Box { modifier.width(2.dp).height(10.dp).align(AlignmentX.Center, AlignmentY.Top).backgroundColor(Color.WHITE) }
            Box { modifier.width(2.dp).height(10.dp).align(AlignmentX.Center, AlignmentY.Bottom).backgroundColor(Color.WHITE) }
        }
    }

    override fun Scene.setupMainScene(ctx: KoolContext) {
        //ctx.isProfileRenderPasses = true
        // no clear needed, we repaint the entire frame, each frame
        mainRenderPass.clearColor = null

        // lighting
        lighting.apply {
            singleLight {
                setDirectional(Vec3f(-1f, -1f, -1f))
                setColor(Color.WHITE, 1f)
            }
        }
        shadowMap = CascadedShadowMap(this@setupMainScene, 0, 300f).apply {
            setMapRanges(0.035f, 0.17f, 1f)
            cascades.forEach {
                it.directionalCamNearOffset = -200f
                it.setDefaultDepthOffset(true)

                oceanFloorPass.dependsOn(it)
            }
        }

        ssao = AoPipeline.createForward(this).apply {
            // negative radius is used to set radius relative to camera distance
            radius = -0.05f
            isEnabled = isSsao.value
            kernelSz = 8
        }

        camLocalGrass.setupGrass(grassColor)
        grass.setupGrass(grassColor)
        trees.setupTrees()

        boxMesh?.let { +it }
        bridgeMesh?.let { +it }

        +trees.treeGroup
        +grass.grassQuads
        +camLocalGrass.grassQuads
        +playerModel
        +terrainTiles
        +ocean.oceanMesh
        +sky.skyGroup
        +physicsObjects.debugLines

        oceanFloorPass.renderGroup += playerModel
        boxMesh?.let { oceanFloorPass.renderGroup += it }

        //defaultCamTransform()

        // setup camera tracking player
        setupCamera(ctx)

        updateTerrainShader(isGroundPbr.value)
        updateOceanShader(isGroundPbr.value)
        updateGrassShader(isVegetationPbr.value)
        updateTreeShader(isVegetationPbr.value)
        updatePlayerShader(isPlayerPbr.value)
        updateBoxShader(isBoxesPbr.value)
        updateBridgeShader(isGroundPbr.value)

        onUpdate += {
            wind.updateWind(it.deltaT)
            sky.updateLight(lighting.lights[0])

            (playerModel.model.meshes.values.first().shader as KslLitShader).apply {
                updateSky(sky.weightedEnvs)
            }
            (bridgeMesh?.shader as KslLitShader).apply {
                updateSky(sky.weightedEnvs)
            }
            (boxMesh?.shader as KslLitShader).apply {
                updateSky(sky.weightedEnvs)
            }
        }
    }

    private fun Scene.setupCamera(ctx: KoolContext) {
        camRig = CharacterTrackingCamRig(ctx.inputMgr).apply {
            camera.setClipRange(0.5f, 5000f)
            trackedPose = physicsObjects.playerController.controller.actor.transform
            +camera
            minZoom = 0.75f
            maxZoom = 100f
            pivotPoint.set(0.25f, 0.75f, 0f)

            setupCollisionAwareCamZoom(physicsObjects.world)

            // hardcoded start look direction
            lookDirection.set(-0.87f, 0.22f, 0.44f).norm()

            // make sure onUpdate listener is called before internal one of CharacterTrackingCamRig, so we can
            // consume the scroll event if the tractor gun is active
            onUpdate.add(0) { ev ->
                // use camera look direction to control player move direction
                physicsObjects.playerController.frontHeading = atan2(lookDirection.x, -lookDirection.z).toDeg()

                val gun = physicsObjects.playerController.tractorGun
                val ptr = ev.ctx.inputMgr.pointerState.primaryPointer
                if (gun.tractorState == TractorGun.TractorState.TRACTOR) {
                    ptr.consume(InputManager.CONSUMED_SCROLL_Y)
                    if (ctx.inputMgr.isShiftDown) {
                        gun.tractorDistance += ptr.deltaScroll.toFloat() * 0.25f
                    } else {
                        gun.rotationTorque += ptr.deltaScroll.toFloat()
                    }
                }
            }
        }
        // don't forget to add the cam rig to the scene
        +camRig
        // player / tractor gun needs the camRig to know where it is aiming at
        physicsObjects.playerController.tractorGun.camRig = camRig
    }

    private fun updateTerrainShader(isGroundPbr: Boolean) {
        terrainTiles.makeTerrainShaders(colorMap, normalMap, terrain.splatMap, shadowMap, ssao.aoMap, isGroundPbr)
    }

    private fun updateOceanShader(isGroundPbr: Boolean) {
        ocean.oceanShader = OceanShader.makeOceanShader(oceanFloorPass, shadowMap, wind.density, oceanBump, isGroundPbr)
    }

    private fun updateTreeShader(isVegetationPbr: Boolean) {
        trees.makeTreeShaders(shadowMap, ssao.aoMap, wind.density, isVegetationPbr)
    }

    private fun updateGrassShader(isVegetationPbr: Boolean) {
        camLocalGrass.grassShader = GrassShader.makeGrassShader(grassColor, shadowMap, ssao.aoMap, wind.density, true, isVegetationPbr)
        grass.grassQuads.children.filterIsInstance<Mesh>().forEach {
            it.shader = GrassShader.makeGrassShader(grassColor, shadowMap, ssao.aoMap, wind.density, false, isVegetationPbr).shader
        }
    }

    private fun updateBoxShader(isBoxesPbr: Boolean) {
        boxMesh?.shader = instancedObjectShader(0.2f, 0.25f, isBoxesPbr)
    }

    private fun updateBridgeShader(isGroundPbr: Boolean) {
        bridgeMesh?.shader = instancedObjectShader(0.8f, 0f, isGroundPbr)
    }

    private fun updatePlayerShader(isPlayerPbr: Boolean) {
        fun KslLitShader.LitShaderConfig.baseConfig() {
            vertices { enableArmature(40) }
            color { constColor(MdColor.PINK.toLinear()) }
            shadow { addShadowMap(shadowMap) }
            enableSsao(ssao.aoMap)
            dualImageBasedAmbientColor()
            colorSpaceConversion = ColorSpaceConversion.LINEAR_TO_sRGB_HDR
        }

        val shader = if (isPlayerPbr) {
            KslPbrShader {
                baseConfig()
                iblConfig()
                roughness(0.2f)
            }

        } else {
            KslBlinnPhongShader {
                baseConfig()
                specularStrength(0.5f)
            }
        }

        playerModel.model.meshes.values.forEach {
            it.shader = shader

            it.normalLinearDepthShader = KslDepthShader(KslDepthShader.Config().apply {
                outputMode = KslDepthShader.OutputMode.NORMAL_LINEAR
                vertices { enableArmature(40) }
            })
        }
    }

    private fun instancedObjectShader(roughness: Float, metallic: Float, isPbr: Boolean): KslShader {
        fun KslLitShader.LitShaderConfig.baseConfig() {
            vertices { isInstanced = true }
            color { vertexColor() }
            shadow { addShadowMap(shadowMap) }
            enableSsao(ssao.aoMap)
            dualImageBasedAmbientColor()
            colorSpaceConversion = ColorSpaceConversion.LINEAR_TO_sRGB_HDR
        }
        return if (isPbr) {
            KslPbrShader {
                baseConfig()
                iblConfig()
                roughness(roughness)
                metallic(metallic)
            }
        } else {
            KslBlinnPhongShader {
                baseConfig()
                specularStrength(1f - roughness)
            }
        }
    }

    private fun makeBoxMesh() = colorMesh {
        generate {
            color = MdColor.PURPLE.toLinear()
            physicsObjects.boxes[0].shapes[0].geometry.generateMesh(this)
        }
        instances = MeshInstanceList(listOf(Attribute.INSTANCE_MODEL_MAT))
        onUpdate += {
            instances!!.apply {
                clear()
                addInstances(physicsObjects.boxes.size) { buf ->
                    physicsObjects.boxes.forEach { box ->
                        buf.put(box.transform.matrix)
                    }
                }
            }
        }
    }

    private fun makeBridgeMesh() = colorMesh {
        generate {
            color = MdColor.BROWN toneLin 700
            cube {
                size.set(2f, 0.2f, 1.1f)
                centered()
            }
        }
        instances = MeshInstanceList(listOf(Attribute.INSTANCE_MODEL_MAT))
        onUpdate += {
            instances?.let { insts ->
                insts.clear()
                insts.addInstances(physicsObjects.chainBridge.segments.size) { buf ->
                    physicsObjects.chainBridge.segments.forEach { seg ->
                        buf.put(seg.transform.matrix)
                    }
                }
            }
        }
    }

    companion object {
        fun KslPbrShader.Config.iblConfig() {
            lightStrength = 3f
        }

        fun KslLitShader.updateSky(envMaps: Sky.WeightedEnvMaps) {
            ambientTextures[0] = envMaps.envA.irradianceMap
            ambientTextures[1] = envMaps.envB.irradianceMap
            ambientTextureWeights = Vec2f(envMaps.weightA, envMaps.weightB)

            if (this is KslPbrShader) {
                reflectionMaps[0] = envMaps.envA.reflectionMap
                reflectionMaps[1] = envMaps.envB.reflectionMap
                reflectionMapWeights = Vec2f(envMaps.weightA, envMaps.weightB)
            }
        }
    }
}