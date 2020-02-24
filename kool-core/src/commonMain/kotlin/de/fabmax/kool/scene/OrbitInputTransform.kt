package de.fabmax.kool.scene

import de.fabmax.kool.InputManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.*
import de.fabmax.kool.util.BoundingBox
import de.fabmax.kool.util.SpringDamperFloat

/**
 * A special kind of transform group which translates mouse input into a orbit transform. This is mainly useful
 * for camera manipulation.
 *
 * @author fabmax
 */

fun orbitInputTransform(name: String? = null, block: OrbitInputTransform.() -> Unit): OrbitInputTransform {
    val sit = OrbitInputTransform(name)
    sit.block()
    return sit
}

fun Scene.defaultCamTransform() {
    +orbitInputTransform {
        // Set some initial rotation so that we look down on the scene
        setMouseRotation(20f, -30f)
        // Add camera to the transform group
        +camera
    }
}

open class OrbitInputTransform(name: String? = null) : TransformGroup(name), Scene.DragHandler {

    var leftDragMethod = DragMethod.ROTATE
    var middleDragMethod = DragMethod.NONE
    var rightDragMethod = DragMethod.PAN
    var zoomMethod = ZoomMethod.ZOOM_TRANSLATE

    var verticalAxis = Vec3f.Y_AXIS
    var horizontalAxis = Vec3f.X_AXIS
    var minHorizontalRot = -90f
    var maxHorizontalRot = 90f

    val translation = MutableVec3f()
    var verticalRotation = 0f
    var horizontalRotation = 0f
    var zoom = 10f
        set(value) {
            field = value.clamp(minZoom, maxZoom)
        }

    var isKeepingStandardTransform = false

    var invertRotX = false
    var invertRotY = false

    var minZoom = 1f
    var maxZoom = 100f
    var translationBounds: BoundingBox? = null

    var panMethod: PanBase = CameraOrthogonalPan()

    val vertRotAnimator = SpringDamperFloat(0f)
    val horiRotAnimator = SpringDamperFloat(0f)
    val zoomAnimator = SpringDamperFloat(zoom)

    private var prevButtonMask = 0
    private var dragMethod = DragMethod.NONE
    private var dragStart = false
    private val deltaPos = MutableVec2f()
    private var deltaScroll = 0f

    private val ptrPos = MutableVec2f()
    private val panPlane = Plane()
    private val pointerHitStart = MutableVec3f()
    private val pointerHit = MutableVec3f()
    private val tmpVec1 = MutableVec3f()
    private val tmpVec2 = MutableVec3f()

    private val mouseTransform = Mat4f()
    private val mouseTransformInv = Mat4f()

    var smoothness: Float = 0f
        set(value) {
            field = value
            val stiffness = if (!value.isFuzzyZero()) { 50.0f / value } else { 0f }
            vertRotAnimator.stiffness = stiffness
            horiRotAnimator.stiffness = stiffness
            zoomAnimator.stiffness = stiffness
        }

    init {
        smoothness = 0.5f
        panPlane.p.set(Vec3f.ZERO)
        panPlane.n.set(Vec3f.Y_AXIS)

        onPreRender += { ctx ->
            doCamTransform(ctx)
        }
    }

    fun setMouseRotation(vertical: Float, horizontal: Float) {
        vertRotAnimator.set(vertical)
        horiRotAnimator.set(horizontal)
        verticalRotation = vertical
        horizontalRotation = horizontal
    }

    fun setMouseTranslation(x: Float, y: Float, z: Float) {
        translation.set(x, y, z)
    }

    fun resetZoom(newZoom: Float) {
        zoom = newZoom
        zoomAnimator.set(zoom)
    }

    fun updateTransform() {
        translationBounds?.clampToBounds(translation)

        if (isKeepingStandardTransform) {
            mouseTransform.invert(mouseTransformInv)
            mul(mouseTransformInv)
        }

        val z = zoomAnimator.actual
        val vr = vertRotAnimator.actual
        val hr = horiRotAnimator.actual
        mouseTransform.setIdentity()
        mouseTransform.translate(translation.x, translation.y, translation.z)
        mouseTransform.scale(z, z, z)
        mouseTransform.rotate(vr, verticalAxis)
        mouseTransform.rotate(hr, horizontalAxis)

        if (isKeepingStandardTransform) {
            mul(mouseTransform)
        } else {
            set(mouseTransform)
        }

    }

    private fun doCamTransform(ctx: KoolContext) {
        val scene = this.scene ?: return

        if (panMethod.computePanPoint(pointerHit, scene, ptrPos, ctx)) {
            if (dragStart) {
                dragStart = false
                pointerHitStart.set(pointerHit)

                // stop any ongoing smooth motion, as we start a new one
                stopSmoothMotion()

            } else if (dragMethod == DragMethod.PAN) {
                val s = (1 - smoothness).clamp(0.1f, 1f)
                tmpVec1.set(pointerHitStart).subtract(pointerHit).scale(s)
                parent?.toLocalCoords(tmpVec1, 0f)

                // limit panning speed
                val tLen = tmpVec1.length()
                if (tLen > scene.camera.globalRange * 0.5f) {
                    tmpVec1.scale(scene.camera.globalRange * 0.5f / tLen)
                }

                translation.add(tmpVec1)
            }
        } else {
            pointerHit.set(scene.camera.globalLookAt)
        }

        if (!deltaScroll.isFuzzyZero()) {
            zoom *= 1f - deltaScroll / 10f
            deltaScroll = 0f
        }

        if (dragMethod == DragMethod.ROTATE) {
            verticalRotation -= deltaPos.x / 3 * if (invertRotX) { -1f } else { 1f }
            horizontalRotation -= deltaPos.y / 3 * if (invertRotY) { -1f } else { 1f }
            horizontalRotation = horizontalRotation.clamp(minHorizontalRot, maxHorizontalRot)
            deltaPos.set(Vec2f.ZERO)
        }

        vertRotAnimator.desired = verticalRotation
        horiRotAnimator.desired = horizontalRotation
        zoomAnimator.desired = zoom

        val oldZ = zoomAnimator.actual
        val z = zoomAnimator.animate(ctx.deltaT)
        if (!isFuzzyEqual(oldZ, z) && zoomMethod == ZoomMethod.ZOOM_TRANSLATE) {
            computeZoomTranslationPerspective(scene, oldZ, z)
        }

        vertRotAnimator.animate(ctx.deltaT)
        horiRotAnimator.animate(ctx.deltaT)
        updateTransform()
    }

    /**
     * Computes the required camera translation so that the camera zooms to the point under the pointer (only works
     * with perspective cameras)
     */
    protected open fun computeZoomTranslationPerspective(scene: Scene, oldZoom: Float, newZoom: Float) {
        // tmpVec1 = zoomed pos on pointer ray
        scene.camera.globalPos.subtract(pointerHit, tmpVec1).scale(newZoom / oldZoom).add(pointerHit)
        // tmpVec2 = zoomed pos on view center ray
        scene.camera.globalPos.subtract(scene.camera.globalLookAt, tmpVec2).scale(newZoom / oldZoom)
                .add(scene.camera.globalLookAt)
        tmpVec1.subtract(tmpVec2)
        parent?.toLocalCoords(tmpVec1, 0f)
        translation.add(tmpVec1)//.subtract(tmpVec2)
    }

    private fun stopSmoothMotion() {
        vertRotAnimator.set(vertRotAnimator.actual)
        horiRotAnimator.set(horiRotAnimator.actual)
        zoomAnimator.set(zoomAnimator.actual)

        verticalRotation = vertRotAnimator.actual
        horizontalRotation = horiRotAnimator.actual
        zoom = zoomAnimator.actual
    }

    override fun onSceneChanged(oldScene: Scene?, newScene: Scene?) {
        super.onSceneChanged(oldScene, newScene)
        oldScene?.removeDragHandler(this)
        newScene?.registerDragHandler(this)
    }

    override fun handleDrag(dragPtrs: List<InputManager.Pointer>, scene: Scene, ctx: KoolContext) {
        if (!dragPtrs.isEmpty() && !dragPtrs[0].isConsumed() && dragPtrs[0].isInViewport(scene.viewport, ctx)) {
            if (dragPtrs[0].buttonEventMask != 0 || dragPtrs[0].buttonMask != prevButtonMask) {
                dragMethod = when {
                    dragPtrs[0].isLeftButtonDown -> leftDragMethod
                    dragPtrs[0].isRightButtonDown -> rightDragMethod
                    dragPtrs[0].isMiddleButtonDown -> middleDragMethod
                    else -> DragMethod.NONE
                }
                dragStart = dragMethod != DragMethod.NONE
            }

            prevButtonMask = dragPtrs[0].buttonMask
            ptrPos.set(dragPtrs[0].x, dragPtrs[0].y)
            deltaPos.set(dragPtrs[0].deltaX, dragPtrs[0].deltaY)
            deltaScroll = dragPtrs[0].deltaScroll
            dragPtrs[0].consume()

        } else {
            deltaPos.set(Vec2f.ZERO)
            deltaScroll = 0f
        }
    }

    enum class DragMethod {
        NONE,
        ROTATE,
        PAN
    }

    enum class ZoomMethod {
        ZOOM_CENTER,
        ZOOM_TRANSLATE
    }
}

abstract class PanBase {
    abstract fun computePanPoint(result: MutableVec3f, scene: Scene, ptrPos: Vec2f, ctx: KoolContext): Boolean
}

class CameraOrthogonalPan : PanBase() {
    val panPlane = Plane()
    private val pointerRay = Ray()

    override fun computePanPoint(result: MutableVec3f, scene: Scene, ptrPos: Vec2f, ctx: KoolContext): Boolean {
        panPlane.p.set(scene.camera.globalLookAt)
        panPlane.n.set(scene.camera.globalLookDir)
        return scene.camera.computePickRay(pointerRay, ptrPos.x, ptrPos.y, scene.viewport, ctx) &&
                panPlane.intersectionPoint(result, pointerRay)
    }
}

class FixedPlanePan(planeNormal: Vec3f) : PanBase() {
    val panPlane = Plane()
    private val pointerRay = Ray()

    init {
        panPlane.n.set(planeNormal)
    }

    override fun computePanPoint(result: MutableVec3f, scene: Scene, ptrPos: Vec2f, ctx: KoolContext): Boolean {
        panPlane.p.set(scene.camera.globalLookAt)
        return scene.camera.computePickRay(pointerRay, ptrPos.x, ptrPos.y, scene.viewport, ctx) &&
                panPlane.intersectionPoint(result, pointerRay)
    }
}

fun xPlanePan() = FixedPlanePan(Vec3f.X_AXIS)
fun yPlanePan() = FixedPlanePan(Vec3f.Y_AXIS)
fun zPlanePan() = FixedPlanePan(Vec3f.Z_AXIS)