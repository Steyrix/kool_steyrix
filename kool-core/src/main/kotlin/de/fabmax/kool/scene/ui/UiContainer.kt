package de.fabmax.kool.scene.ui

import de.fabmax.kool.platform.RenderContext
import de.fabmax.kool.util.BoundingBox
import de.fabmax.kool.util.MutableVec3f
import de.fabmax.kool.util.Vec3f
import de.fabmax.kool.util.isEqual

/**
 * @author fabmax
 */

open class UiContainer(name: String, root: UiRoot) : UiComponent(name, root) {

    val posInParent = MutableVec3f()
    private var isLayoutNeeded = true
    private val tmpChildBounds = BoundingBox()

    fun requestLayout() {
        isLayoutNeeded = true
    }

    override fun updateTheme(ctx: RenderContext) {
        super.updateTheme(ctx)
        for (i in children.indices) {
            val child = children[i]
            if (child is UiComponent) {
                child.requestThemeUpdate()
            }
        }
    }

    override fun updateComponentAlpha() {
        super.updateComponentAlpha()
        for (i in children.indices) {
            val child = children[i]
            if (child is UiComponent) {
                child.alpha = alpha
            }
        }
    }

    override fun render(ctx: RenderContext) {
        if (isLayoutNeeded) {
            isLayoutNeeded = false
            doLayout(contentBounds, ctx)
        }
        super.render(ctx)
    }

    override fun doLayout(bounds: BoundingBox, ctx: RenderContext) {
        applyBounds(bounds, ctx)
        for (i in children.indices) {
            val child = children[i]
            if (child is UiComponent) {
                computeChildLayoutBounds(tmpChildBounds, child, ctx)
                child.doLayout(tmpChildBounds, ctx)
            }
        }
    }

    override fun createThemeUi(ctx: RenderContext): ComponentUi {
        return root.theme.containerUi(this)
    }

    protected open fun applyBounds(bounds: BoundingBox, ctx: RenderContext) {
        if (!isEqual(bounds.size, contentBounds.size) || !isEqual(bounds.min, contentBounds.min)) {
            posInParent.set(bounds.min)
            setIdentity().translate(bounds.min)
            contentBounds.set(Vec3f.ZERO, bounds.size)
            requestUiUpdate()
        }
    }

    protected open fun computeChildLayoutBounds(result: BoundingBox, child: UiComponent, ctx: RenderContext) {
        var x = child.layoutSpec.x.toUnits(contentBounds.size.x, dpi)
        var y = child.layoutSpec.y.toUnits(contentBounds.size.y, dpi)
        var z = child.layoutSpec.z.toUnits(contentBounds.size.z, dpi)
        val w = child.layoutSpec.width.toUnits(contentBounds.size.x, dpi)
        val h = child.layoutSpec.height.toUnits(contentBounds.size.y, dpi)
        val d = child.layoutSpec.depth.toUnits(contentBounds.size.z, dpi)

        if (x < 0) { x += width }
        if (y < 0) { y += height }
        if (z < 0) { z += depth }

        result.set(x, y, z, x + w, y + h, z + d)
    }
}