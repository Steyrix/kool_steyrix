package de.fabmax.kool.editor

import de.fabmax.kool.input.*

open class EditorKeyListener(name: String) : InputStack.InputHandler(name) {
    private val _registeredKeys = mutableMapOf<Key, InputStack.SimpleKeyListener>()
    val registeredKeys: List<Key> get() = _registeredKeys.keys.sorted()

    init {
        blockAllKeyboardInput = true
    }

    fun push() {
        InputStack.pushTop(this)
    }

    fun pop() {
        InputStack.remove(this)
    }

    fun addKeyListener(key: Key, block: (KeyEvent) -> Unit) {
        check(key !in _registeredKeys) { "$key already registered" }
        val binding = key.binding
        _registeredKeys[key] = addKeyListener(binding.keyCode, key.name, binding.keyMod.keyEventFilter, block)
    }

    fun removeKeyListener(key: Key) {
        _registeredKeys.remove(key)?.let { removeKeyListener(it) }
    }

    companion object {
        fun cancelListener(name: String = "Cancel operation", block: (KeyEvent) -> Unit): EditorKeyListener {
            return EditorKeyListener(name).apply {
                addKeyListener(Key.CancelOperation, block)
            }
        }
    }
}

enum class Key(val group: KeyGroup) {
    Copy(KeyGroup.General),
    Paste(KeyGroup.General),
    Duplicate(KeyGroup.General),

    Undo(KeyGroup.General),
    Redo(KeyGroup.General),

    CancelOperation(KeyGroup.General),
    DeleteSelected(KeyGroup.General),
    HideSelected(KeyGroup.General),
    UnhideHidden(KeyGroup.General),
    FocusSelected(KeyGroup.General),

    ToggleBoxSelectMode(KeyGroup.General),
    ToggleMoveMode(KeyGroup.General),
    ToggleRotateMode(KeyGroup.General),
    ToggleScaleMode(KeyGroup.General),
    ToggleImmediateMoveMode(KeyGroup.General),
    ToggleImmediateRotateMode(KeyGroup.General),
    ToggleImmediateScaleMode(KeyGroup.General),


    LimitToXAxis(KeyGroup.ImmediateTransform),
    LimitToYAxis(KeyGroup.ImmediateTransform),
    LimitToZAxis(KeyGroup.ImmediateTransform),
    LimitToXPlane(KeyGroup.ImmediateTransform),
    LimitToYPlane(KeyGroup.ImmediateTransform),
    LimitToZPlane(KeyGroup.ImmediateTransform),
    ;

    val binding: KeyBinding get() = getBinding(this)

    companion object {
        fun getBinding(key: Key): KeyBinding {
            // todo: load these from settings
            return when (key) {
                Copy -> KeyBinding(key, LocalKeyCode('C'), KeyMod.ctrl)
                Paste -> KeyBinding(key, LocalKeyCode('V'), KeyMod.ctrl)
                Duplicate -> KeyBinding(key, LocalKeyCode('D'), KeyMod.ctrl)
                Undo -> KeyBinding(key, LocalKeyCode('Z'), KeyMod.ctrl)
                Redo -> KeyBinding(key, LocalKeyCode('Y'), KeyMod.ctrl)
                CancelOperation -> KeyBinding(key, KeyboardInput.KEY_ESC, KeyMod.none)
                DeleteSelected -> KeyBinding(key, KeyboardInput.KEY_DEL, KeyMod.none)
                HideSelected -> KeyBinding(key, LocalKeyCode('H'), KeyMod.none)
                UnhideHidden -> KeyBinding(key, LocalKeyCode('H'), KeyMod.alt)
                FocusSelected -> KeyBinding(key, KeyboardInput.KEY_NP_DECIMAL, KeyMod.none)
                ToggleBoxSelectMode -> KeyBinding(key, LocalKeyCode('B'), KeyMod.none)
                ToggleMoveMode -> KeyBinding(key, LocalKeyCode('G'), KeyMod.alt)
                ToggleRotateMode -> KeyBinding(key, LocalKeyCode('R'), KeyMod.alt)
                ToggleScaleMode -> KeyBinding(key, LocalKeyCode('S'), KeyMod.alt)
                ToggleImmediateMoveMode -> KeyBinding(key, LocalKeyCode('G'), KeyMod.none)
                ToggleImmediateRotateMode -> KeyBinding(key, LocalKeyCode('R'), KeyMod.none)
                ToggleImmediateScaleMode -> KeyBinding(key, LocalKeyCode('S'), KeyMod.none)
                LimitToXAxis -> KeyBinding(key, LocalKeyCode('X'), KeyMod.none)
                LimitToYAxis -> KeyBinding(key, LocalKeyCode('Y'), KeyMod.none)
                LimitToZAxis -> KeyBinding(key, LocalKeyCode('Z'), KeyMod.none)
                LimitToXPlane -> KeyBinding(key, LocalKeyCode('X'), KeyMod.shift)
                LimitToYPlane -> KeyBinding(key, LocalKeyCode('Y'), KeyMod.shift)
                LimitToZPlane -> KeyBinding(key, LocalKeyCode('Z'), KeyMod.shift)
            }
        }
    }
}

data class KeyBinding(val key: Key, val keyCode: KeyCode, val keyMod: KeyMod) {
    val name: String get() = key.toString()

    val keyName: String get() {
        var s = keyMod.toString()
        if (s.isNotEmpty()) {
            s += " + "
        }
        return s + keyCode.name
    }
}

class KeyMod(val mask: Int) {
    val isAlt: Boolean = (mask and ALT) != 0
    val isCtrl: Boolean = (mask and CTRL) != 0
    val isShift: Boolean = (mask and SHIFT) != 0

    val keyEventFilter: (KeyEvent) -> Boolean = { ev ->
        ev.isPressed &&
                isAlt == ev.isAltDown &&
                isCtrl == ev.isCtrlDown &&
                isShift == ev.isShiftDown
    }

    override fun toString(): String {
        var s = ""
        if (isCtrl) s += "CTRL + "
        if (isAlt) s += "ALT + "
        if (isShift) s += "SHIFT"
        return s.removeSuffix(" + ")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as KeyMod
        return mask == other.mask
    }

    override fun hashCode(): Int = mask

    companion object {
        const val ALT = 1
        const val CTRL = 2
        const val SHIFT = 4

        val none = KeyMod(0)
        val alt = KeyMod(ALT)
        val ctrl = KeyMod(CTRL)
        val shift = KeyMod(SHIFT)
        val altCtrl = KeyMod(ALT + CTRL)
        val altShift = KeyMod(ALT + SHIFT)
        val ctrlShift = KeyMod(CTRL + SHIFT)
        val altCtrlShift = KeyMod(ALT + CTRL + SHIFT)
    }
}

enum class KeyGroup {
    General,
    ImmediateTransform
}
