package de.fabmax.kool.platform

import de.fabmax.kool.KoolContext
import de.fabmax.kool.KoolSystem
import de.fabmax.kool.pipeline.backend.gl.RenderBackendGlImpl
import de.fabmax.kool.util.Log
import de.fabmax.kool.util.RenderLoopCoroutineDispatcher
import de.fabmax.kool.util.Time
import de.fabmax.kool.util.logE

typealias AndroidLog = android.util.Log

class AndroidContext : KoolContext() {
    override val backend: RenderBackendGlImpl

    override val windowWidth: Int
        get() = backend.viewWidth
    override val windowHeight: Int
        get() = backend.viewHeight

    // todo: not really applicable on android?
    override var isFullscreen: Boolean
        get() = false
        set(_) { }

    private var prevFrameTime = System.nanoTime()

    init {
        check(!KoolSystem.isContextCreated) { "KoolContext was already created" }

        // todo: set correct value
        windowScale = 2f

        backend = RenderBackendGlImpl(this)
        KoolSystem.onContextCreated(this)
    }

    override fun openUrl(url: String, sameWindow: Boolean) {
        logE { "Open URL: $url" }
    }

    override fun run() {

    }

    override fun getSysInfos(): List<String> {
        return emptyList()
    }

    internal fun renderFrame() {
        RenderLoopCoroutineDispatcher.executeDispatchedTasks()

        // determine time delta
        val time = System.nanoTime()
        val dt = (time - prevFrameTime) / 1e9
        prevFrameTime = time

        // setup draw queues for all scenes / render passes
        render(dt)

        // execute draw queues
        backend.renderFrame(this)
    }

    companion object {
        init {
            if (Log.printer == Log.DEFAULT_PRINTER) {
                Log.printer = { lvl, tag, message ->
                    val ctx = KoolSystem.getContextOrNull()
                    val frmTxt = ctx?.let { "f:${Time.frameCount}  " } ?: ""
                    val txt = "$frmTxt$message"
                    when (lvl) {
                        Log.Level.TRACE -> AndroidLog.d(tag, "TRC: $txt")
                        Log.Level.DEBUG -> AndroidLog.d(tag, txt)
                        Log.Level.INFO -> AndroidLog.i(tag, txt)
                        Log.Level.WARN -> AndroidLog.w(tag, txt)
                        Log.Level.ERROR -> AndroidLog.e(tag, txt)
                        Log.Level.OFF -> { }
                    }
                }
            }
        }
    }
}