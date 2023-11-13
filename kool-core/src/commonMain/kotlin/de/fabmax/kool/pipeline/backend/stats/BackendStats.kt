package de.fabmax.kool.pipeline.backend.stats

object BackendStats {

    val offscreenPasses = mutableMapOf<Long, OffscreenPassInfo>()

    val allocatedBuffers = mutableMapOf<Long, BufferInfo>()
    var totalBufferSize: Long = 0L
        internal set


    val allocatedTextures = mutableMapOf<Long, TextureInfo>()
    var totalTextureSize: Long = 0L
        internal set

    var numDrawCommands = 0
        private set
    var numPrimitives = 0
        private set

    fun resetPerFrameCounts() {
        numDrawCommands = 0
        numPrimitives = 0
    }

    fun addDrawCommandCount(nCommands: Int) {
        numDrawCommands += nCommands
    }

    fun addPrimitiveCount(nPrimitives: Int) {
        numPrimitives += nPrimitives
    }
}