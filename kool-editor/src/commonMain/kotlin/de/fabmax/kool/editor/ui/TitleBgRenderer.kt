package de.fabmax.kool.editor.ui

import de.fabmax.kool.math.Vec4f
import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.blocks.mvpMatrix
import de.fabmax.kool.modules.ksl.lang.*
import de.fabmax.kool.modules.ui2.Ui2Shader
import de.fabmax.kool.modules.ui2.UiNode
import de.fabmax.kool.modules.ui2.UiRenderer
import de.fabmax.kool.modules.ui2.UiSurface
import de.fabmax.kool.pipeline.Attribute
import de.fabmax.kool.pipeline.BlendMode
import de.fabmax.kool.pipeline.CullMethod
import de.fabmax.kool.pipeline.GlslType
import de.fabmax.kool.scene.Mesh
import de.fabmax.kool.scene.MeshInstanceList
import de.fabmax.kool.scene.geometry.IndexedVertexList
import de.fabmax.kool.util.MdColor

class TitleBgRenderer(
    val topRadius: Float = 0f,
    val bottomRadius: Float = 0f
) : UiRenderer<UiNode> {

    override fun renderUi(node: UiNode) {
        val meshLayer = node.surface.getMeshLayer(node.modifier.zLayer + UiSurface.LAYER_BACKGROUND)
        val bgMesh = TitleBgMesh.forSurface(node.surface)
        val isFirstUsage = meshLayer.addCustomLayer("title-bg", 0) { bgMesh }
        if (isFirstUsage) {
            bgMesh.bgInstances.clear()
            bgMesh.bgShader.bgColor = UiColors.titleBg

            val s = node.sizes.lineHeightTitle.px
            bgMesh.bgShader.fadeProps = Vec4f(s * 1.5f, s, s * 5.5f, 0.2f)
        }

        bgMesh.bgInstances.addInstance {
            put(node.clipBoundsPx.array)
            put(node.leftPx)
            put(node.topPx)
            put(node.widthPx)
            put(node.heightPx)
            put(topRadius)
            put(bottomRadius)
        }
    }

    private class TitleBgMesh : Mesh(IndexedVertexList(Ui2Shader.UI_MESH_ATTRIBS)) {
        val bgInstances = MeshInstanceList(listOf(
            Ui2Shader.ATTRIB_CLIP,
            TitleBgShader.ATTRIB_DIMENS,
            TitleBgShader.ATTRIB_CLIP_CORNERS
        ))

        val bgShader = TitleBgShader()

        init {
            shader = bgShader
            instances = bgInstances
            generate {
                centeredRect {
                    origin.set(0.5f, 0.5f, 0f)
                    size.set(1f, 1f)
                }
            }
        }

        companion object {
            val surfaceMeshes = mutableMapOf<UiSurface, TitleBgMesh>()

            fun forSurface(surface: UiSurface): TitleBgMesh {
                return surfaceMeshes.getOrPut(surface) { TitleBgMesh() }
            }
        }
    }

    private class TitleBgShader : KslShader(Model(), pipelineConfig) {
        var accentColor by uniform4f("uAccentColor", MdColor.CYAN)
        var bgColor by uniform4f("uBgColor", UiColors.titleBg)
        var fadeProps by uniform4f("uFadeProps")

        private class Model : KslProgram("Demo category shader") {
            init {
                val screenPos = interStageFloat2()
                val localPos = interStageFloat2()
                val size = interStageFloat2()
                val clipBounds = interStageFloat4(interpolation = KslInterStageInterpolation.Flat)
                val clipCornerRadius = interStageFloat2(interpolation = KslInterStageInterpolation.Flat)

                vertexStage {
                    main {
                        clipBounds.input set instanceAttribFloat4(Ui2Shader.ATTRIB_CLIP.name)
                        clipCornerRadius.input set instanceAttribFloat2(ATTRIB_CLIP_CORNERS.name)

                        val pos = float3Var(vertexAttribFloat3(Attribute.POSITIONS.name))
                        val dimens = float4Var(instanceAttribFloat4(ATTRIB_DIMENS.name))
                        size.input set dimens.zw
                        localPos.input set pos.xy * dimens.zw

                        pos.xy set dimens.xy + pos.xy * dimens.zw
                        screenPos.input set pos.xy

                        outPosition set mvpMatrix().matrix * float4Value(pos, 1f.const)
                    }
                }
                fragmentStage {
                    main {
                        `if` (all(screenPos.output gt clipBounds.output.xy) and
                                all(screenPos.output lt clipBounds.output.zw)) {

                            // rounded upper corners
                            val p = screenPos.output
                            var r = clipCornerRadius.output.x
                            val lt = clipBounds.output.x
                            val up = clipBounds.output.y
                            val rt = clipBounds.output.z
                            val dn = clipBounds.output.w
                            val cLt = float2Var(float2Value(lt + r, up + r))
                            val cRt = float2Var(float2Value(rt - r, up + r))
                            `if` ((all(p lt cLt) and (length(cLt - p) gt r)) or
                                    ((p.x gt cRt.x) and (p.y lt cRt.y) and (length(cRt - p) gt r))) {
                                discard()
                            }

                            // rounded bottom corners
                            r = clipCornerRadius.output.y
                            cLt set float2Value(lt + r, dn - r)
                            cRt set float2Value(rt - r, dn - r)
                            `if` ((all(p gt cRt) and (length(cRt - p) gt r)) or
                                    ((p.x lt cLt.x) and (p.y gt cLt.y) and (length(cLt - p) gt r))) {
                                discard()
                            }

                            val fadeProps = uniformFloat4("uFadeProps")
                            val fadeCenter = fadeProps.xy
                            val fadeRadius = fadeProps.z
                            val accentStr = fadeProps.a
                            val fadePos = float1Var(length(localPos.output - fadeCenter) / fadeRadius)
                            val fadeWgt = float1Var(smoothStep(0f.const, 1f.const, fadePos) * accentStr + (1f.const - accentStr))

                            colorOutput(mix(uniformFloat4("uAccentColor"), uniformFloat4("uBgColor"), fadeWgt))
                        }.`else` {
                            discard()
                        }
                    }
                }
            }
        }

        companion object {
            val ATTRIB_DIMENS = Attribute("aDimens", GlslType.VEC_4F)
            val ATTRIB_CLIP_CORNERS = Attribute("aClipCorners", GlslType.VEC_2F)

            val pipelineConfig = PipelineConfig().apply {
                blendMode = BlendMode.DISABLED
                cullMethod = CullMethod.NO_CULLING
                //depthTest = DepthCompareOp.DISABLED
            }
        }
    }
}