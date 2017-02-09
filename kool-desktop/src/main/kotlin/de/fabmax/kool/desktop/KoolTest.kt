package de.fabmax.kool.desktop

import de.fabmax.kool.demo.simpleShapesDemo
import de.fabmax.kool.demo.textureDemo
import de.fabmax.kool.platform.Platform
import de.fabmax.kool.platform.PlatformImpl
import de.fabmax.kool.platform.lwjgl3.Lwjgl3Context

/**
 * @author fabmax
 */
fun main(args: Array<String>) {
    PlatformImpl.init()

    val props = Lwjgl3Context.InitProps()
    val ctx = Platform.createContext(props)

    simpleShapesDemo(ctx)
    //textureDemo(ctx)
}
