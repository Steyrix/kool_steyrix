package de.fabmax.kool.editor.components

import de.fabmax.kool.editor.api.AppAssets
import de.fabmax.kool.editor.api.GameEntity
import de.fabmax.kool.editor.data.*
import de.fabmax.kool.math.*
import de.fabmax.kool.physics.*
import de.fabmax.kool.physics.geometry.*
import de.fabmax.kool.pipeline.Attribute
import de.fabmax.kool.scene.Mesh
import de.fabmax.kool.scene.Model
import de.fabmax.kool.scene.TrsTransformF
import de.fabmax.kool.scene.geometry.IndexedVertexList
import de.fabmax.kool.util.BufferedList
import de.fabmax.kool.util.launchOnMainThread

context(GameEntityComponent)
val RigidActor.component: RigidActorComponent? get() {
    val world = sceneEntity.getComponent<PhysicsWorldComponent>()
    return world?.actors?.get(this)
}

context(GameEntityComponent)
val RigidActor.gameEntity: GameEntity? get() {
    val world = sceneEntity.getComponent<PhysicsWorldComponent>()
    return world?.actors?.get(this)?.gameEntity
}

class RigidActorComponent(
    gameEntity: GameEntity,
    componentInfo: ComponentInfo<RigidActorComponentData> = ComponentInfo(RigidActorComponentData())
) :
    PhysicsComponent<RigidActorComponentData>(gameEntity, componentInfo),
    MeshComponent.ListenerComponent,
    ModelComponent.ListenerComponent
{
    var rigidActor: RigidActor? = null
        private set

    private var geometry: List<CollisionGeometry> = emptyList()
    private var bodyShapes: List<ShapeData> = emptyList()

    val triggerListeners = BufferedList<TriggerListener>()
    private val proxyTriggerListener = object : TriggerListener {
        override fun onActorEntered(trigger: RigidActor, actor: RigidActor) {
            triggerListeners.updated().forEach {
                it.onActorEntered(trigger, actor)
            }
        }

        override fun onActorExited(trigger: RigidActor, actor: RigidActor) {
            triggerListeners.updated().forEach {
                it.onActorExited(trigger, actor)
            }
        }
    }

    override val globalActorTransform: TrsTransformF? get() = rigidActor?.transform

    init {
        dependsOn(MeshComponent::class, isOptional = true)
        dependsOn(ModelComponent::class, isOptional = true)

        data.shapes
            .filterIsInstance<ShapeData.Heightmap>()
            .filter { it.mapPath.isNotBlank() }
            .forEach { requiredAssets += it.toAssetReference() }
    }

    override fun onDataChanged(oldData: RigidActorComponentData, newData: RigidActorComponentData) {
        launchOnMainThread {
            updateRigidActor(newData)
        }
    }

    fun addTriggerListener(listener: TriggerListener) {
        triggerListeners += listener
    }

    fun removeTriggerListener(listener: TriggerListener) {
        triggerListeners += listener
    }

    override suspend fun applyComponent() {
        super.applyComponent()
        createRigidBody(data)
    }

    override fun destroyComponent() {
        rigidActor?.let {
            physicsWorld?.removeActor(it)
            it.release()
        }
        rigidActor = null
        geometry.forEach { it.release() }
        geometry = emptyList()
        super.destroyComponent()
    }

    private suspend fun updateRigidActor(actorData: RigidActorComponentData) {
        val actor = rigidActor
        val isActorOk = when (actorData.actorType) {
            RigidActorType.DYNAMIC -> actor is RigidDynamic && !actor.isKinematic
            RigidActorType.KINEMATIC -> actor is RigidDynamic && actor.isKinematic
            RigidActorType.STATIC -> actor is RigidStatic
        }

        if (!isActorOk || actorData.shapes != bodyShapes) {
            createRigidBody(actorData)

        } else if (actor is RigidDynamic) {
            actor.mass = actorData.mass.toFloat()
            actor.updateInertiaFromShapesAndMass()
            actor.characterControllerHitBehavior = actorData.characterControllerHitBehavior
        }

        actor?.apply {
            if (isTrigger != actorData.isTrigger) {
                isTrigger = actorData.isTrigger
                if (isTrigger) {
                    physicsWorld?.registerTriggerListener(this, proxyTriggerListener)
                } else {
                    physicsWorld?.unregisterTriggerListener(proxyTriggerListener)
                }
            }
        }
    }

    private suspend fun createRigidBody(actorData: RigidActorComponentData) {
        val physicsWorldComponent = getOrCreatePhysicsWorldComponent()

        rigidActor?.let {
            physicsWorldComponent.removeActor(this)
            it.release()
        }
        geometry.forEach { it.release() }

        rigidActor = when (actorData.actorType) {
            RigidActorType.DYNAMIC -> RigidDynamic(actorData.mass.toFloat())
            RigidActorType.KINEMATIC -> RigidDynamic(actorData.mass.toFloat(), isKinematic = true)
            RigidActorType.STATIC -> RigidStatic()
        }

        scale.set(Vec3d.ONES)

        requiredAssets.clear()
        rigidActor?.let { actor ->
            bodyShapes = actorData.shapes
            val meshComp = gameEntity.getComponent<MeshComponent>()

            warnOnNonUniformScale = true
            val shapes = when {
                bodyShapes.isNotEmpty() -> bodyShapes.mapNotNull { shape -> shape.makeCollisionGeometry() }
                meshComp != null -> meshComp.makeCollisionShapes()
                else -> emptyList()
            }

            shapes.forEach { (shape, pose) -> actor.attachShape(Shape(shape, localPose = pose)) }
            geometry = shapes.map { it.first }
            physicsWorldComponent.addActor(this@RigidActorComponent)
        }

        updateRigidActor(actorData)
        setPhysicsTransformFromDrawNode()
    }

    private suspend fun MeshComponent.makeCollisionShapes(): List<Pair<CollisionGeometry, Mat4f>> {
        return when (val node = drawNode) {
            is Mesh -> data.shapes.mapNotNull { shape -> shape.makeCollisionGeometry(node) }
            is Model -> node.makeCollisionShapes()
            else -> emptyList()
        }
    }

    private fun Model.makeCollisionShapes(): List<Pair<CollisionGeometry, Mat4f>> {
        transform.decompose(scale = scale)
        warnOnNonUniformScale = false

        val collisionGeom = IndexedVertexList(Attribute.POSITIONS)
        val globalToModel = invModelMatD
        meshes.values.forEach { mesh ->
            val meshToModel = mesh.modelMatD * globalToModel
            collisionGeom.addGeometry(mesh.geometry) {
                meshToModel.transform(position, 1f)
            }
        }
        return listOf(collisionGeom.makeTriMeshGeometry(scale.toVec3f()) to Mat4f.IDENTITY)
    }

    private suspend fun ShapeData.makeCollisionGeometry(mesh: Mesh? = null): Pair<CollisionGeometry, Mat4f>? {
        return when (this) {
            is ShapeData.Box -> BoxGeometry(size.toVec3f()) to Mat4f.IDENTITY
            is ShapeData.Capsule -> CapsuleGeometry(length.toFloat(), radius.toFloat()) to Mat4f.IDENTITY
            is ShapeData.Cylinder -> CylinderGeometry(length.toFloat(), bottomRadius.toFloat()) to Mat4f.IDENTITY
            is ShapeData.Sphere -> SphereGeometry(radius.toFloat()) to Mat4f.IDENTITY
            is ShapeData.Heightmap -> loadHeightmapGeometry(this)?.let { it to Mat4f.IDENTITY }
            is ShapeData.Plane -> PlaneGeometry() to Mat4f.rotation(90f.deg, Vec3f.Z_AXIS)
            is ShapeData.Rect -> mesh?.let { it.geometry.makeTriMeshGeometry(Vec3f.ONES) to Mat4f.IDENTITY }
            else -> null
        }
    }

    private fun IndexedVertexList.makeTriMeshGeometry(scale: Vec3f): TriangleMeshGeometry {
        return TriangleMeshGeometry(this, scale)
    }

    private suspend fun loadHeightmapGeometry(shapeData: ShapeData.Heightmap): CollisionGeometry? {
        if (shapeData.mapPath.isBlank()) {
            return null
        }
        val heightmapRef = shapeData.toAssetReference()
        requiredAssets += heightmapRef
        val heightmap = AppAssets.loadHeightmap(heightmapRef) ?: return null
        val heightField = HeightField(heightmap, shapeData.rowScale.toFloat(), shapeData.colScale.toFloat())
        return HeightFieldGeometry(heightField)
    }

    override fun applyPose(position: Vec3d, rotation: QuatD) {
        rigidActor?.apply {
            this.position = position.toVec3f()
            this.rotation = rotation.toQuatF()
        }
    }

    override suspend fun onMeshGeometryChanged(component: MeshComponent, newData: MeshComponentData) {
        if (data.shapes.isEmpty()) {
            createRigidBody(data)
        }
    }

    override suspend fun onModelChanged(component: ModelComponent, newData: ModelComponentData) {
        if (data.shapes.isEmpty()) {
            createRigidBody(data)
        }
    }
}