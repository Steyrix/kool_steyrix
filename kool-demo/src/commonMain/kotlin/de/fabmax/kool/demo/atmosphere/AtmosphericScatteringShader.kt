package de.fabmax.kool.demo.atmosphere

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.pipeline.deferred.DeferredPassSwapListener
import de.fabmax.kool.pipeline.deferred.DeferredPasses
import de.fabmax.kool.pipeline.shadermodel.*
import de.fabmax.kool.pipeline.shading.ModeledShader
import de.fabmax.kool.pipeline.shading.Texture2dInput
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.logW
import kotlin.math.pow

class AtmosphericScatteringShader : ModeledShader(atmosphereModel()), DeferredPassSwapListener {

    private var atmosphereNode: AtmosphereNode? = null

    val opticalDepthLut = Texture2dInput("tOpticalDepthLut")
    val sceneColor = Texture2dInput("tSceneColor")
    val scenePos = Texture2dInput("tScenePos")
    val skyColor = Texture2dInput("tSkyColor")

    var dirToSun = Vec3f(0f, 0f, 1f)
        set(value) {
            field = value
            atmosphereNode?.uDirToSun?.value?.set(value)
        }
    var sunColor = Color.WHITE
        set(value) {
            field = value
            atmosphereNode?.uSunColor?.value?.set(value)
        }

    var scatteringCoeffPow = 4.5f
        set(value) {
            field = value
            updateScatteringCoeffs()
        }
    var scatteringCoeffStrength = 1f
        set(value) {
            field = value
            updateScatteringCoeffs()
        }
    var scatteringCoeffs = Vec3f(0.5f, 0.8f, 1.0f)
        set(value) {
            field = value
            updateScatteringCoeffs()
        }

    var rayleighColor = Color(0.75f, 0.75f, 0.75f, 1f)
        set(value) {
            field = value
            atmosphereNode?.uRayleighColor?.value?.set(value)
        }

    var mieColor = Color(1f, 0.9f, 0.8f, 0.3f)
        set(value) {
            field = value
            atmosphereNode?.uMieColor?.value?.set(value)
        }
    var mieG = 0.99f
        set(value) {
            field = value
            atmosphereNode?.uMieG?.value = value
        }

    var planetCenter = Vec3f.ZERO
        set(value) {
            field = value
            atmosphereNode?.uPlanetCenter?.value?.set(value)
        }
    var surfaceRadius = 600f
        set(value) {
            field = value
            atmosphereNode?.uSurfaceRadius?.value = value
        }
    var atmosphereRadius = 609f
        set(value) {
            field = value
            atmosphereNode?.uAtmosphereRadius?.value = value
        }

    init {
        onPipelineSetup += { builder, _, _ ->
            builder.depthTest = DepthCompareOp.DISABLED
        }
        onPipelineCreated += { _, _, _ ->
            opticalDepthLut.connect(model)
            sceneColor.connect(model)
            scenePos.connect(model)
            skyColor.connect(model)

            atmosphereNode = model.findNodeByType()
            atmosphereNode?.apply {
                uDirToSun.value.set(dirToSun)
                uPlanetCenter.value.set(planetCenter)
                uSurfaceRadius.value = surfaceRadius
                uAtmosphereRadius.value = atmosphereRadius
                uRayleighColor.value.set(rayleighColor)
                uMieColor.value.set(mieColor)
                uMieG.value = mieG
                uSunColor.value.set(sunColor)
                updateScatteringCoeffs()
            }
        }
    }

    private fun updateScatteringCoeffs() {
        atmosphereNode?.uScatteringCoeffs?.value?.let {
            it.x = scatteringCoeffs.x.pow(scatteringCoeffPow) * scatteringCoeffStrength
            it.y = scatteringCoeffs.y.pow(scatteringCoeffPow) * scatteringCoeffStrength
            it.z = scatteringCoeffs.z.pow(scatteringCoeffPow) * scatteringCoeffStrength
        }
    }

    companion object {
        private fun atmosphereModel() = ShaderModel("atmo-scattering-shader").apply {
            val mvp: UniformBufferMvp
            val ifQuadPos: StageInterfaceNode
            val ifViewDir: StageInterfaceNode

            vertexStage {
                mvp = mvpNode()
                val quadPos = attrTexCoords().output
                val viewDir = addNode(XyToViewDirNode(stage)).apply {
                    inScreenPos = quadPos
                }
                ifViewDir = stageInterfaceNode("ifViewDir", viewDir.outViewDir)
                ifQuadPos = stageInterfaceNode("ifTexCoords", quadPos)
                positionOutput = fullScreenQuadPositionNode(quadPos).outQuadPos
            }
            fragmentStage {
                addNode(RaySphereIntersectionNode(stage))

                val fragMvp = mvp.addToStage(stage)
                val opticalDepthLut = texture2dNode("tOpticalDepthLut")
                val sceneColor = texture2dSamplerNode(texture2dNode("tSceneColor"), ifQuadPos.output).outColor
                val skyColor = texture2dSamplerNode(texture2dNode("tSkyColor"), ifQuadPos.output).outColor
                val viewPos = texture2dSamplerNode(texture2dNode("tScenePos"), ifQuadPos.output).outColor
                val view2world = addNode(ViewToWorldPosNode(stage)).apply {
                    inViewPos = viewPos
                }

                val atmoNd = addNode(AtmosphereNode(opticalDepthLut, stage)).apply {
                    inSceneColor = sceneColor
                    inSceneDepth = splitNode(viewPos, "z").output
                    inScenePos = view2world.outWorldPos
                    inSkyColor = skyColor
                    inViewDepth = splitNode(viewPos, "z").output
                    inCamPos = fragMvp.outCamPos
                    inLookDir = ifViewDir.output
                }

                colorOutput(hdrToLdrNode(atmoNd.outColor).outColor)
            }
        }
    }

    private class XyToViewDirNode(graph: ShaderGraph) : ShaderNode("xyToViewDir", graph) {
        private val uInvViewProjMat = UniformMat4f("uInvViewProjMat")

        var inScreenPos = ShaderNodeIoVar(ModelVar2fConst(Vec2f.ZERO))
        val outViewDir = ShaderNodeIoVar(ModelVar3f("${name}_outWorldPos"), this)

        override fun setup(shaderGraph: ShaderGraph) {
            super.setup(shaderGraph)
            dependsOn(inScreenPos)
            shaderGraph.descriptorSet.apply {
                uniformBuffer(name, shaderGraph.stage) {
                    +{ uInvViewProjMat }
                    onUpdate = { _, cmd ->
                        uInvViewProjMat.value.set(cmd.renderPass.camera.invViewProj)
                    }
                }
            }
        }

        override fun generateCode(generator: CodeGenerator) {
            generator.appendMain("""
                vec4 ${name}_near = $uInvViewProjMat * vec4(${inScreenPos.ref2f()} * 2.0 - 1.0, 0.0, 1.0);
                vec4 ${name}_far = $uInvViewProjMat * vec4(${inScreenPos.ref2f()} * 2.0 - 1.0, 1.0, 1.0);
                ${outViewDir.declare()} = (${name}_far.xyz / ${name}_far.w) - (${name}_near.xyz / ${name}_near.w);
            """)
        }
    }

    private class ViewToWorldPosNode(graph: ShaderGraph) : ShaderNode("view2worldPos", graph) {
        private val uInvViewMat = UniformMat4f("uInvViewMat")

        var inViewPos = ShaderNodeIoVar(ModelVar3fConst(Vec3f.ZERO))
        val outWorldPos = ShaderNodeIoVar(ModelVar4f("${name}_outWorldPos"), this)

        override fun setup(shaderGraph: ShaderGraph) {
            super.setup(shaderGraph)
            dependsOn(inViewPos)
            shaderGraph.descriptorSet.apply {
                uniformBuffer(name, shaderGraph.stage) {
                    +{ uInvViewMat }
                    onUpdate = { _, cmd ->
                        uInvViewMat.value.set(cmd.renderPass.camera.invView)
                    }
                }
            }
        }

        override fun generateCode(generator: CodeGenerator) {
            generator.appendMain("${outWorldPos.declare()} = $uInvViewMat * vec4(${inViewPos.ref3f()}, 1.0);")
        }
    }

    override fun onSwap(previousPasses: DeferredPasses, currentPasses: DeferredPasses) {
        sceneColor(currentPasses.lightingPass.colorTexture)
        scenePos(currentPasses.materialPass.positionFlags)
    }
}


class RaySphereIntersectionNode(graph: ShaderGraph) : ShaderNode("raySphereIntersection", graph) {
    override fun generateCode(generator: CodeGenerator) {
        generator.appendFunction("raySphereIntersection", """
            bool raySphereIntersection(vec3 rayOrigin, vec3 rayDir, vec3 center, float radius, out float d1, out float d2) {
                vec3 centerToOri = rayOrigin - center;
                float a = dot(rayDir, rayDir);
                float b = 2.0 * dot(rayDir, centerToOri);
                float c = dot(centerToOri, centerToOri) - radius * radius;
                
                float discriminant = b * b - 4.0 * a * c;
                if (discriminant < 0.0) {
                    return false;
                } else {
                    float q = -0.5 * (b + sign(b) * sqrt(discriminant));
                    float t1 = q / a;
                    float t2 = c / q;
                    d1 = min(t1, t2);
                    d2 = max(t1, t2);
                    return d2 > 0.0;
                }
            }
        """)

        generator.appendFunction("rayPointDistance", """
            vec4 rayPointDistance(vec3 rayOrigin, vec3 rayDir, vec3 point) {
                vec3 w = point - rayOrigin;
                float c1 = dot(w, rayDir);
                float c2 = dot(rayDir, rayDir);
                vec3 pn = rayOrigin + rayDir * (c1 / c2);
                float dist = length(pn - point) * sign(c1);
                return vec4(pn, dist);
            }
        """)
    }
}

class AtmosphereNode(val opticalDepthLut: Texture2dNode, graph: ShaderGraph) : ShaderNode("atmosphereNode", graph) {
    var inSceneColor = ShaderNodeIoVar(ModelVar4fConst(Color.BLACK))
    var inSkyColor = ShaderNodeIoVar(ModelVar4fConst(Color.BLACK))
    var inSceneDepth = ShaderNodeIoVar(ModelVar1fConst(0f))
    var inViewDepth = ShaderNodeIoVar(ModelVar1fConst(0f))
    var inScenePos = ShaderNodeIoVar(ModelVar3fConst(Vec3f.ZERO))
    var inCamPos = ShaderNodeIoVar(ModelVar3fConst(Vec3f.ZERO))
    var inLookDir = ShaderNodeIoVar(ModelVar3fConst(Vec3f.Z_AXIS))

    var inSunShadow: Texture2dNode? = null
    var inSunShadowProj: ShaderNodeIoVar? = null

    val uDirToSun = Uniform3f("uDirToSun")
    val uPlanetCenter = Uniform3f("uPlanetCenter")
    val uSurfaceRadius = Uniform1f("uSurfaceRadius")
    val uAtmosphereRadius = Uniform1f("uAtmosphereRadius")
    val uScatteringCoeffs = Uniform3f("uScatteringCoeffs")
    val uMieG = Uniform1f("uMieG")
    val uMieColor = UniformColor("uMieColor")
    val uRayleighColor = UniformColor("uRayleighColor")
    val uSunColor = UniformColor("uSunColor")
    val uRandomOffset = Uniform2f("uAtmoRandOffset")

    val outColor = ShaderNodeIoVar(ModelVar4f("outColor"), this)

    var numScatterSamples = 16
    var randomizeStartOffsets = true

    override fun setup(shaderGraph: ShaderGraph) {
        super.setup(shaderGraph)
        dependsOn(inSceneColor, inSceneDepth, inScenePos, inSkyColor, inViewDepth, inCamPos, inLookDir)

        inSunShadowProj?.let { dependsOn(it) }
        inSunShadow?.let {
            if (!it.isDepthTexture) {
                logW { "sunShadow texture is supposed to be a depth texture" }
            }
            dependsOn(it)
        }

        shaderGraph.descriptorSet.apply {
            uniformBuffer(name, shaderGraph.stage) {
                +{ uDirToSun }
                +{ uPlanetCenter }
                +{ uRayleighColor }
                +{ uMieColor }
                +{ uScatteringCoeffs }
                +{ uSunColor }
                +{ uSurfaceRadius }
                +{ uAtmosphereRadius }
                +{ uMieG }
                +{ uRandomOffset }

                onUpdate = { _, _ ->
                    uRandomOffset.value.x = (uRandomOffset.value.x + 4711f) % 100000f
                    uRandomOffset.value.y = (uRandomOffset.value.y + 1337f) % 100000f
                }
            }
        }
    }

    override fun generateCode(generator: CodeGenerator) {
        generator.appendFunction("phaseFunRayleigh", """
            vec3 phaseFunRayleigh(float cosTheta) {
                //vec3 phase = vec3(1.0 + clamp(cosTheta, 0.0, 1.0));
                //vec3 phase = vec3(1.0 + smoothstep(0.0, 1.0, cosTheta));
                vec3 phase = vec3(1.5 + cosTheta * 0.5);
                return phase * $uRayleighColor.rgb * $uRayleighColor.a;
            }
        """)

        generator.appendFunction("phaseFunMie", """
            vec3 phaseFunMie(float cosTheta, float g) {
                float g2 = g * g;
                float f1 = (3.0 * (1.0 - g2)) / (2.0 * (2.0 + g2));
                float f2 = (1.0 + cosTheta * cosTheta) / pow(1.0 + g2 - 2.0 * g * cosTheta, 1.5);
                return vec3(f1 * f2) * $uMieColor.rgb * $uMieColor.a;
            }
        """)

        generator.appendFunction("opticalDepthLut", """
            vec2 opticalDepthLut(float altitude, float cosTheta) {
                return ${generator.sampleTexture2d(opticalDepthLut.name, "vec2(cosTheta * 0.5 + 0.5, altitude)")}.xy;
            }
        """)

        generator.appendFunction("opticalDepthLenLut", """
            float opticalDepthLenLut(vec3 origin, vec3 dir, float len) {
                float atmosphereThickness = $uAtmosphereRadius - $uSurfaceRadius;
                
                vec3 p1 = origin;
                vec3 p2 = origin + dir * len;
                float altitude1 = (length(p1) - $uSurfaceRadius) / atmosphereThickness;
                float altitude2 = (length(p2) - $uSurfaceRadius) / atmosphereThickness;
                
                if (altitude1 > altitude2) {
                    // swap points and direction if ray is pointing downwards
                    p1 = p2;
                    p2 = origin;
                    
                    float swapAlt = altitude1;
                    altitude1 = altitude2;
                    altitude2 = swapAlt;
                    
                    dir = -dir;
                }
                
                float depth1 = opticalDepthLut(altitude1, dot(dir, normalize(p1))).x;
                float depth2 = opticalDepthLut(altitude2, dot(dir, normalize(p2))).x;
                return depth1 - depth2;
            }
        """)

        val sunShadow = inSunShadow
        val sunProj = inSunShadowProj
        if (sunShadow != null && sunProj != null) {
            // light scatter variant considering a shadow map, this assumes inSunShadow and inSunShadowProj are both uniforms
            generator.appendFunction("scatterLight", """
                vec3 scatterLight(vec3 origin, vec3 dir, float rayLength, vec3 dirToSun) {
                    float atmosphereThickness = $uAtmosphereRadius - $uSurfaceRadius;
                    float stepSize = rayLength / float(${numScatterSamples + 1});
                    vec3 inScatterPt = origin;
                    vec3 inScatteredLight = vec3(0.0);
                    
                    // random offset in range (-0.5..0.5) * stepSize
                    float r = float(int(gl_FragCoord.x + $uRandomOffset.x) * 18913541 * int(gl_FragCoord.y + $uRandomOffset.y) * 31845071) / 4294967296.0;
                    inScatterPt += dir * stepSize * r;
                    
                    for (int i = 0; i < $numScatterSamples; i++) {
                        inScatterPt += dir * stepSize;
                        
                        vec4 posLightSpaceProj = ${sunProj.name} * vec4(inScatterPt, 1.0);
                        float shadowFac = ${generator.sampleTexture2dDepth(sunShadow.name, "posLightSpaceProj")};
                        
                        float dPlanetIn, dPlanetOut;
                        bool planetHit = raySphereIntersection(inScatterPt, dirToSun, vec3(0.0), $uSurfaceRadius, dPlanetIn, dPlanetOut);
                        if (!planetHit && shadowFac > 0.01) {
                            vec3 verticalDir = normalize(inScatterPt);
                            float cosTheta = dot(dirToSun, verticalDir);
                            float altitude = (length(inScatterPt) - $uSurfaceRadius) / atmosphereThickness;

                            float viewRayOpticalDepth = opticalDepthLenLut(origin, dir, stepSize * float(i));
                            vec2 opticalDepthToSun = opticalDepthLut(altitude, cosTheta);
                            float sunRayOpticalDepth = opticalDepthToSun.x;
                            float localDensity = opticalDepthToSun.y;
                            vec3 transmittance = exp(-(sunRayOpticalDepth + viewRayOpticalDepth) * $uScatteringCoeffs);

                            inScatteredLight += localDensity * transmittance * $uScatteringCoeffs * stepSize * shadowFac;
                        }
                    }
                    
                    float sunAngle = dot(dir, dirToSun);
                    vec3 rayleigh = phaseFunRayleigh(sunAngle);
                    vec3 mie = phaseFunMie(sunAngle, $uMieG);
                    return (rayleigh + mie) * inScatteredLight * $uSunColor.rgb * $uSunColor.a;
                }
            """)

        } else {
            val startRayOffset = if (randomizeStartOffsets) {
                "float(int(gl_FragCoord.x + $uRandomOffset.x) * 18913541 * int(gl_FragCoord.y + $uRandomOffset.y) * 31845071) / 4294967296.0"
            } else {
                "0.0"
            }

            generator.appendFunction("scatterLight", """
                vec3 scatterLight(vec3 origin, vec3 dir, float rayLength, vec3 dirToSun) {
                    float atmosphereThickness = $uAtmosphereRadius - $uSurfaceRadius;
                    float stepSize = rayLength / float(${numScatterSamples + 1});
                    vec3 inScatterPt = origin;
                    vec3 inScatteredLight = vec3(0.0);
                    
                    // random offset in range (-0.5..0.5) * stepSize
                    float r = $startRayOffset;
                    inScatterPt += dir * stepSize * r;
                    
                    for (int i = 0; i < $numScatterSamples; i++) {
                        inScatterPt += dir * stepSize;
                        
                        float dPlanetIn, dPlanetOut;
                        bool planetHit = raySphereIntersection(inScatterPt, dirToSun, vec3(0.0), $uSurfaceRadius, dPlanetIn, dPlanetOut);
                        if (!planetHit) {
                            vec3 verticalDir = normalize(inScatterPt);
                            float cosTheta = dot(dirToSun, verticalDir);
                            float altitude = (length(inScatterPt) - $uSurfaceRadius) / atmosphereThickness;
    
                            float viewRayOpticalDepth = opticalDepthLenLut(origin, dir, stepSize * float(i));
                            vec2 opticalDepthToSun = opticalDepthLut(altitude, cosTheta);
                            float sunRayOpticalDepth = opticalDepthToSun.x;
                            float localDensity = opticalDepthToSun.y;
                            vec3 transmittance = exp(-(sunRayOpticalDepth + viewRayOpticalDepth) * $uScatteringCoeffs);
    
                            inScatteredLight += localDensity * transmittance * $uScatteringCoeffs * stepSize;
                        }
                    }
                    
                    float sunAngle = dot(dir, dirToSun);
                    vec3 rayleigh = phaseFunRayleigh(sunAngle);
                    vec3 mie = phaseFunMie(sunAngle, $uMieG);
                    return (rayleigh + mie) * inScatteredLight * $uSunColor.rgb * $uSunColor.a;
                }
            """)
        }

        generator.appendMain("""
            float dAtmoIn, dAtmoOut;
            vec3 camOri = ${inCamPos.ref3f()} - $uPlanetCenter;
            vec3 nrmLookDir = normalize(${inLookDir.ref3f()});
            bool hitAtmo = raySphereIntersection(camOri, nrmLookDir, vec3(0.0), $uAtmosphereRadius, dAtmoIn, dAtmoOut);
            
            ${outColor.declare()} = $inSceneColor;
            if ($inSceneDepth > 0.0) {
                $outColor = $inSkyColor;
            }
            if (hitAtmo) {
                // dAtmoIn is negative if camera is inside atmosphere
                float dToAtmo = max(0.0, dAtmoIn);
                float dThroughAtmo = dAtmoOut - dToAtmo;
                if (${inViewDepth.ref1f()} < 0.0) {
                    float sceneDepth = length(${inCamPos.ref3f()} - ${inScenePos.ref3f()});
                    dThroughAtmo = min(dThroughAtmo, sceneDepth - dToAtmo);
                }

                // scattering
                vec3 atmoHitPt = camOri + nrmLookDir * dToAtmo;
                vec3 light = scatterLight(atmoHitPt, nrmLookDir, dThroughAtmo, $uDirToSun);
                $outColor.rgb += light;
            }
        """)
    }
}