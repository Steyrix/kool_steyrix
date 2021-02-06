package de.fabmax.kool.physics

import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec3f
import physx.PxTopLevelFunctions
import physx.common.PxVec3
import physx.physics.PxScene
import physx.physics.PxSceneDesc
import physx.physics.PxSceneFlagEnum

actual class PhysicsWorld actual constructor(gravity: Vec3f, numWorkers: Int) : CommonPhysicsWorld() {
    val scene: PxScene

    private val bufPxGravity = gravity.toPxVec3(PxVec3())
    private val bufGravity = MutableVec3f()
    actual var gravity: Vec3f
        get() = scene.gravity.toVec3f(bufGravity)
        set(value) {
            scene.gravity = value.toPxVec3(bufPxGravity)
        }

    init {
        val sceneDesc = PxSceneDesc(Physics.physics.tolerancesScale)
        sceneDesc.gravity = bufPxGravity
        sceneDesc.cpuDispatcher = PxTopLevelFunctions.DefaultCpuDispatcherCreate(8)
        sceneDesc.filterShader = PxTopLevelFunctions.DefaultFilterShader()
        sceneDesc.flags.set(PxSceneFlagEnum.eENABLE_CCD)
        scene = Physics.physics.createScene(sceneDesc)
    }

    override fun singleStepPhysicsImpl(timeStep: Float) {
        scene.simulate(timeStep)
        scene.fetchResults(true)
    }

    override fun addActor(actor: RigidActor) {
        super.addActor(actor)
        scene.addActor(actor.pxRigidActor)
    }

    override fun removeActor(actor: RigidActor) {
        super.removeActor(actor)
        scene.removeActor(actor.pxRigidActor)
    }
}