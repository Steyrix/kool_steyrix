package de.fabmax.kool.physics

import de.fabmax.kool.math.Mat4f
import org.lwjgl.system.MemoryStack
import physx.physics.PxRigidActor
import physx.physics.PxRigidStatic

actual class RigidStatic actual constructor(pose: Mat4f) : RigidActor() {

    private val pxRigidStatic: PxRigidStatic

    override val pxRigidActor: PxRigidActor

    init {
        MemoryStack.stackPush().use { mem ->
            val pxPose = pose.toPxTransform(mem.createPxTransform())
            pxRigidStatic = Physics.physics.createRigidStatic(pxPose)
            pxRigidActor = pxRigidStatic
        }
        transform.setMatrix(pose)
    }
}