package de.fabmax.kool.platform

import de.fabmax.kool.InputManager
import de.fabmax.kool.util.logD
import org.lwjgl.glfw.GLFW.*

class JvmInputManager(private val windowHandle: Long, private val ctx: Lwjgl3Context) : InputManager() {

    private val localCharKeyCodes = mutableMapOf<Char, Char>()

    override var cursorMode: CursorMode = CursorMode.NORMAL
        set(value) {
            field = value
            if (value == CursorMode.NORMAL || ctx.isWindowFocued) {
                glfwSetInputMode(windowHandle, GLFW_CURSOR, value.glfwMode)
            }
        }

    init {
        installInputHandlers()
        ctx.onFocusChanged += { isFocused ->
            if (cursorMode == CursorMode.LOCKED) {
                if (!isFocused) {
                    logD { "Switching to normal cursor mode because of focus loss" }
                    glfwSetInputMode(windowHandle, GLFW_CURSOR, GLFW_CURSOR_NORMAL)
                } else {
                    logD { "Re-engaging cursor-lock because of focus gain" }
                    glfwSetInputMode(windowHandle, GLFW_CURSOR, GLFW_CURSOR_DISABLED)
                }
            }
        }

        for (c in 'A'..'Z') {
            val localName = glfwGetKeyName(c.code, 0) ?: ""
            if (localName.isNotBlank()) {
                val localChar = localName[0].uppercaseChar()
                localCharKeyCodes[localChar] = c
            }
        }
    }

    override fun getKeyCodeForChar(char: Char, useLocalKeyboardLayout: Boolean): Int {
        var lookUpChar = char
        if (useLocalKeyboardLayout) {
            lookUpChar = localCharKeyCodes[char.uppercaseChar()] ?: char
        }
        return super.getKeyCodeForChar(lookUpChar, false)
    }

    private fun installInputHandlers() {
        // install mouse callbacks
        glfwSetMouseButtonCallback(windowHandle) { _, btn, act, _ ->
            handleMouseButtonState(btn, act == GLFW_PRESS)
        }
        glfwSetCursorPosCallback(windowHandle) { _, x, y ->
            handleMouseMove(x, y)
        }
        glfwSetCursorEnterCallback(windowHandle) { _, entered ->
            if (!entered) {
                handleMouseExit()
            }
        }
        glfwSetScrollCallback(windowHandle) { _, _, yOff ->
            handleMouseScroll(yOff)
        }

        // install keyboard callbacks
        glfwSetKeyCallback(windowHandle) { _, key, _, action, mods ->
            val event = when (action) {
                GLFW_PRESS -> KEY_EV_DOWN
                GLFW_REPEAT -> KEY_EV_DOWN or KEY_EV_REPEATED
                GLFW_RELEASE -> KEY_EV_UP
                else -> -1
            }
            if (event != -1) {
                val keyCode = Lwjgl3Context.KEY_CODE_MAP[key] ?: key
                var keyMod = 0
                if (mods and GLFW_MOD_ALT != 0) {
                    keyMod = keyMod or KEY_MOD_ALT
                }
                if (mods and GLFW_MOD_CONTROL != 0) {
                    keyMod = keyMod or KEY_MOD_CTRL
                }
                if (mods and GLFW_MOD_SHIFT != 0) {
                    keyMod = keyMod or KEY_MOD_SHIFT
                }
                if (mods and GLFW_MOD_SUPER != 0) {
                    keyMod = keyMod or KEY_MOD_SUPER
                }
                keyEvent(keyCode, keyMod, event)
            }
        }
        glfwSetCharCallback(windowHandle) { _, codepoint ->
            charTyped(codepoint.toChar())
        }
    }

    private val CursorMode.glfwMode: Int
        get() = when (this) {
            CursorMode.NORMAL -> GLFW_CURSOR_NORMAL
            CursorMode.LOCKED -> GLFW_CURSOR_DISABLED
        }
}