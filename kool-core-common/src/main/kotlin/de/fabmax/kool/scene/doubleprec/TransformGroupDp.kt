package de.fabmax.kool.scene.doubleprec

import de.fabmax.kool.KoolContext
import de.fabmax.kool.RenderPass
import de.fabmax.kool.math.Mat4d
import de.fabmax.kool.math.Mat4dStack
import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.RayTest
import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.BoundingBox

fun transformGroupDp(name: String? = null, block: TransformGroupDp.() -> Unit): TransformGroupDp {
    val tg = TransformGroupDp(name)
    tg.block()
    return tg
}

open class TransformGroupDp(name: String? = null) : NodeDp(name) {

    protected val children: MutableList<NodeDp> = mutableListOf()
    protected val tmpBounds = BoundingBox()

    protected val globalTransform = Mat4d()
    protected val transform = Mat4d()
    protected val invTransform = Mat4d()
    protected var isIdentity = false
    protected var isDirty = false

    private val tmpTransformVec = MutableVec3f()

    val size: Int get() = children.size

    override fun onSceneChanged(oldScene: Scene?, newScene: Scene?) {
        super.onSceneChanged(oldScene, newScene)
        for (i in children.indices) {
            children[i].scene = newScene
        }
    }

    protected fun checkInverse() {
        if (isDirty) {
            transform.invert(invTransform)
            isDirty = false
        }
    }

    protected fun setDirty() {
        isDirty = true
        isIdentity = false
    }

    override fun preRenderDp(ctx: KoolContext, modelMatDp: Mat4dStack) {
        // apply transformation
        val wasIdentity = isIdentity
        if (!wasIdentity) {
            modelMatDp.push().mul(transform)
            globalTransform.set(modelMatDp)
            ctx.mvpState.modelMatrix.set(modelMatDp)
            ctx.mvpState.update(ctx)
        }

        // call preRender on all children and update group bounding box
        tmpBounds.clear()
        for (i in children.indices) {
            children[i].preRenderDp(ctx, modelMatDp)
            tmpBounds.add(children[i].bounds)
        }
        bounds.set(tmpBounds)

        // transform group bounds
        if (!bounds.isEmpty && !wasIdentity) {
            tmpBounds.clear()
            tmpBounds.add(transform.transform(tmpTransformVec.set(bounds.min.x, bounds.min.y, bounds.min.z), 1f))
            tmpBounds.add(transform.transform(tmpTransformVec.set(bounds.min.x, bounds.min.y, bounds.max.z), 1f))
            tmpBounds.add(transform.transform(tmpTransformVec.set(bounds.min.x, bounds.max.y, bounds.min.z), 1f))
            tmpBounds.add(transform.transform(tmpTransformVec.set(bounds.min.x, bounds.max.y, bounds.max.z), 1f))
            tmpBounds.add(transform.transform(tmpTransformVec.set(bounds.max.x, bounds.min.y, bounds.min.z), 1f))
            tmpBounds.add(transform.transform(tmpTransformVec.set(bounds.max.x, bounds.min.y, bounds.max.z), 1f))
            tmpBounds.add(transform.transform(tmpTransformVec.set(bounds.max.x, bounds.max.y, bounds.min.z), 1f))
            tmpBounds.add(transform.transform(tmpTransformVec.set(bounds.max.x, bounds.max.y, bounds.max.z), 1f))
            bounds.set(tmpBounds)
        }

        // clear transformation
        if (!wasIdentity) {
            modelMatDp.pop()
            ctx.mvpState.modelMatrix.set(modelMatDp)
            ctx.mvpState.update(ctx)
        }

        // compute global position and size based on group bounds and current model transform
        super.preRender(ctx)
    }

    override fun render(ctx: KoolContext) {
        if (isVisible) {
            // apply transformation
            val wasIdentity = isIdentity
            if (!wasIdentity) {
                ctx.mvpState.modelMatrix.push()
                ctx.mvpState.modelMatrix.set(globalTransform)
                ctx.mvpState.update(ctx)
            }

            // draw all child nodes
            super.render(ctx)
            if (isRendered) {
                for (i in children.indices) {
                    if (ctx.renderPass != RenderPass.SHADOW || children[i].isCastingShadow) {
                        children[i].render(ctx)
                    }
                }
            }

            // clear transformation
            if (!wasIdentity) {
                ctx.mvpState.modelMatrix.pop()
                ctx.mvpState.update(ctx)
            }
        }
    }

    override fun toGlobalCoords(vec: MutableVec3f, w: Float): MutableVec3f {
        if (!isIdentity) {
            transform.transform(vec, w)
        }
        return super.toGlobalCoords(vec, w)
    }

    override fun toLocalCoords(vec: MutableVec3f, w: Float): MutableVec3f {
        super.toLocalCoords(vec, w)
        if (!isIdentity) {
            checkInverse()
            return invTransform.transform(vec, w)
        } else {
            return vec
        }
    }

    override fun rayTest(test: RayTest) {
        if (!isIdentity) {
            // transform ray to local coordinates
            checkInverse()
            invTransform.transform(test.ray.origin, 1f)
            invTransform.transform(test.ray.direction, 0f)
        }

        super.rayTest(test)

        if (!isIdentity) {
            // transform ray back to previous coordinates
            transform.transform(test.ray.origin, 1f)
            transform.transform(test.ray.direction, 0f)
        }
    }

    fun translate(tx: Double, ty: Double, tz: Double): TransformGroupDp {
        transform.translate(tx, ty, tz)
        setDirty()
        return this
    }

    fun rotate(angleDeg: Double, axX: Double, axY: Double, axZ: Double): TransformGroupDp {
        transform.rotate(angleDeg, axX, axY, axZ)
        setDirty()
        return this
    }

    fun scale(sx: Double, sy: Double, sz: Double): TransformGroupDp {
        transform.scale(sx, sy, sz)
        setDirty()
        return this
    }

    fun mul(mat: Mat4d): TransformGroupDp {
        transform.mul(mat)
        setDirty()
        return this
    }

    fun set(mat: Mat4d): TransformGroupDp {
        transform.set(mat)
        setDirty()
        return this
    }

    fun setIdentity(): TransformGroupDp {
        transform.setIdentity()
        invTransform.setIdentity()
        isDirty = false
        isIdentity = true
        return this
    }

    open fun addNode(node: NodeDp, index: Int = -1) {
        if (index >= 0) {
            children.add(index, node)
        } else {
            children.add(node)
        }
        node.parent = this
        bounds.add(node.bounds)
    }

    open fun removeNode(node: NodeDp): Boolean {
        if (children.remove(node)) {
            node.parent = null
            return true
        }
        return false
    }

    open fun containsNode(node: NodeDp): Boolean = children.contains(node)

    operator fun plusAssign(node: NodeDp) {
        addNode(node)
    }

    operator fun minusAssign(node: NodeDp) {
        removeNode(node)
    }

    operator fun NodeDp.unaryPlus() {
        addNode(this)
    }

    operator fun Node.unaryPlus() {
        addNode(NodeProxy(this))
    }
}