package de.fabmax.kool.editor.ui

import de.fabmax.kool.editor.actions.RemoveComponentAction
import de.fabmax.kool.editor.components.EditorModelComponent
import de.fabmax.kool.editor.data.NodeId
import de.fabmax.kool.editor.model.NodeModel
import de.fabmax.kool.editor.model.SceneModel
import de.fabmax.kool.editor.util.sceneModel
import de.fabmax.kool.math.Vec2d
import de.fabmax.kool.math.Vec3d
import de.fabmax.kool.math.Vec4d
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.MdColor

abstract class ComponentEditor<T: EditorModelComponent>() : Composable {
    var components: List<T> = emptyList()
    val component: T get() = components[0]

    val nodeId: NodeId get() = component.nodeModel.nodeId
    val nodeModel: NodeModel get() = component.nodeModel
    val sceneModel: SceneModel get() = nodeModel.sceneModel

    protected fun removeComponent() {
        RemoveComponentAction(nodeId, component).apply()
    }

    protected fun condenseDouble(doubles: List<Double>): Double {
        return if (doubles.all { it == doubles[0] }) doubles[0] else Double.NaN
    }

    protected fun condenseVec2(vecs: List<Vec2d>): Vec2d {
        val x = if (vecs.all { it.x == vecs[0].x }) vecs[0].x else Double.NaN
        val y = if (vecs.all { it.y == vecs[0].y }) vecs[0].y else Double.NaN
        return Vec2d(x, y)
    }

    protected fun condenseVec3(vecs: List<Vec3d>): Vec3d {
        val x = if (vecs.all { it.x == vecs[0].x }) vecs[0].x else Double.NaN
        val y = if (vecs.all { it.y == vecs[0].y }) vecs[0].y else Double.NaN
        val z = if (vecs.all { it.z == vecs[0].z }) vecs[0].z else Double.NaN
        return Vec3d(x, y, z)
    }

    protected fun condenseVec4(vecs: List<Vec4d>): Vec4d {
        val x = if (vecs.all { it.x == vecs[0].x }) vecs[0].x else Double.NaN
        val y = if (vecs.all { it.y == vecs[0].y }) vecs[0].y else Double.NaN
        val z = if (vecs.all { it.z == vecs[0].z }) vecs[0].z else Double.NaN
        val w = if (vecs.all { it.w == vecs[0].w }) vecs[0].w else Double.NaN
        return Vec4d(x, y, z, w)
    }

    protected fun mergeDouble(masked: Double, fromComponent: Double): Double {
        return if (masked.isFinite()) masked else fromComponent
    }

    protected fun mergeVec2(masked: Vec2d, fromComponent: Vec2d): Vec2d {
        return Vec2d(
            if (masked.x.isFinite()) masked.x else fromComponent.x,
            if (masked.y.isFinite()) masked.y else fromComponent.y,
        )
    }

    protected fun mergeVec3(masked: Vec3d, fromComponent: Vec3d): Vec3d {
        return Vec3d(
            if (masked.x.isFinite()) masked.x else fromComponent.x,
            if (masked.y.isFinite()) masked.y else fromComponent.y,
            if (masked.z.isFinite()) masked.z else fromComponent.z,
        )
    }

    protected fun mergeVec4(masked: Vec4d, fromComponent: Vec4d): Vec4d {
        return Vec4d(
            if (masked.x.isFinite()) masked.x else fromComponent.x,
            if (masked.y.isFinite()) masked.y else fromComponent.y,
            if (masked.z.isFinite()) masked.z else fromComponent.z,
            if (masked.w.isFinite()) masked.w else fromComponent.w,
        )
    }
}

fun UiScope.componentPanel(
    title: String,
    imageIcon: IconProvider? = null,
    onRemove: (() -> Unit)? = null,
    titleWidth: Dimension = Grow.Std,
    headerContent: (RowScope.() -> Unit)? = null,
    block: ColumnScope.() -> Any?
) = collapsapsablePanel(
    title,
    imageIcon,
    titleWidth = titleWidth,
    headerContent = {
        headerContent?.invoke(this)
        onRemove?.let { remove ->
            Box {
                var isHovered by remember(false)
                val fgColor = colors.onBackground
                val bgColor = if (isHovered) MdColor.RED else colors.componentBg

                modifier
                    .alignY(AlignmentY.Center)
                    .margin(end = sizes.gap * 0.75f)
                    .padding(sizes.smallGap * 0.5f)
                    .onEnter { isHovered = true }
                    .onExit { isHovered = false }
                    .onClick { remove() }
                    .background(CircularBackground(bgColor))

                Image {
                    modifier.iconImage(IconMap.small.trash, fgColor)
                }
            }
        }
    },
    block = block
)
