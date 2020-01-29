package de.fabmax.kool.platform.vk.pipeline

import de.fabmax.kool.pipeline.Pipeline
import de.fabmax.kool.platform.vk.RenderPass
import de.fabmax.kool.platform.vk.SwapChain
import de.fabmax.kool.platform.vk.VkSystem

class PipelineManager(val sys: VkSystem) {

    private val onScreenPipelineConfigs = mutableSetOf<Pipeline>()

    private var swapChain: SwapChain? = null
    private val mutPipelines = mutableMapOf<ULong, GraphicsPipeline>()
    private val pipelines: Map<ULong, GraphicsPipeline> = mutPipelines

    fun onSwapchainDestroyed() {
        swapChain = null
        mutPipelines.clear()
    }

    fun onSwapchainCreated(swapChain: SwapChain) {
        this.swapChain = swapChain
        onScreenPipelineConfigs.forEach { createOnScreenPipeline(it, swapChain.renderPass) }
    }

    fun hasPipeline(pipeline: Pipeline, renderPass: Long): Boolean = pipelines.containsKey(makeKey(pipeline, renderPass))

    fun addPipelineConfig(pipeline: Pipeline, nImages: Int, renderPass: RenderPass, dynVp: Boolean) {
        if (renderPass === swapChain?.renderPass) {
            if (onScreenPipelineConfigs.add(pipeline)) {
                createOnScreenPipeline(pipeline, renderPass)
            }
        } else {
            val gp = GraphicsPipeline(sys, renderPass, 1, dynVp, pipeline, nImages)
            sys.device.addDependingResource(gp)
            mutPipelines[makeKey(pipeline, renderPass.vkRenderPass)] = gp
        }
    }

    private fun createOnScreenPipeline(pipeline: Pipeline, renderPass: RenderPass) {
        val swapChain = this.swapChain ?: return
        val gp = GraphicsPipeline(sys, renderPass, sys.physicalDevice.msaaSamples, false, pipeline, swapChain.nImages)
        swapChain.addDependingResource(gp)
        mutPipelines[makeKey(pipeline, swapChain.renderPass.vkRenderPass)] = gp
    }

    fun getPipeline(pipeline: Pipeline, renderPass: Long): GraphicsPipeline {
        return mutPipelines[makeKey(pipeline, renderPass)] ?: throw NoSuchElementException("Unknown pipeline config: ${pipeline.pipelineHash}")
    }

    private fun makeKey(pipeline: Pipeline, renderPass: Long): ULong = pipeline.pipelineHash xor renderPass.toULong()
}