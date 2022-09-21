package de.fabmax.kool.modules.ui2

import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MdColor

/**
 * UI colors. Somewhat based on the Material Design color system:
 *   https://material.io/design/color/the-color-system.html#color-theme-creation
 * However, primary and secondary color are replaced by a single accent color.
 *
 * - [accent]: Accent color used by UI elements.
 * - [accentVariant]: A little less prominent than the main accent color.
 * - [background]: Appears behind scrollable content.
 * - [surface]: Used on surfaces of components, such as menus.
 * - [error]: Indicates errors in components, such as invalid text in a text field.
 * - [onAccent]: Used for icons and text displayed on top of the secondary color.
 * - [onBackground]: Used for icons and text displayed on top of the background color.
 * - [onSurface]: Used for icons and text displayed on top of the surface color.
 * - [onError]: Used for icons and text displayed on top of the error color.
 * - [isLight]: Whether this color is considered as a 'light' or 'dark' set of colors.
 */
data class Colors(
    val accent: Color,
    val accentVariant: Color,
    val background: Color,
    val surface: Color,
    val error: Color,
    val onAccent: Color,
    val onBackground: Color,
    val onSurface: Color,
    val onError: Color,
    val isLight: Boolean
) {
    companion object {
        fun darkColors(
            accent: Color = MdColor.LIGHT_BLUE,
            accentVariant: Color = MdColor.LIGHT_BLUE tone 800,
            background: Color = Color("202020d0"),
            surface: Color = Color("101010d0"),
            error: Color = Color("b00020"),
            onAccent: Color = Color.BLACK,
            onBackground: Color = Color.WHITE,
            onSurface: Color = Color.WHITE,
            onError: Color = Color.BLACK,
            isLight: Boolean = false
        ): Colors = Colors(
            accent,
            accentVariant,
            background,
            surface,
            error,
            onAccent,
            onBackground,
            onSurface,
            onError,
            isLight
        )
    }
}
