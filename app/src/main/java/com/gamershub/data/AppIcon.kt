package com.gamershub.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Launcher icon choice. Every look ships in the APK as an <activity-alias> entry
 * in the manifest, and exactly one alias is enabled at a time, so the home screen
 * shows the icon the user picked.
 *
 * Index 0 is the gamepad on "Auto": its plate is @color/ic_pad_bg, which has a
 * -night variant, so the launcher repaints the icon when the phone switches
 * between light and dark. That happens in the resource system — no code, and it
 * works even while the app is dead.
 */
object AppIcon {
    private val ALIASES = arrayOf("IconPadAuto", "IconPadDark", "IconPadLight", "IconPadMono")
    private const val PREFS = "gamershub"
    private const val KEY = "appIcon"

    const val COUNT = 4

    /** The gamepad that follows the system theme. Default for new installs. */
    const val AUTO = 0
    const val DARK = 1
    const val LIGHT = 2
    const val MONO = 3

    fun current(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, AUTO).coerceIn(0, COUNT - 1)

    fun label(index: Int): String = when (index) {
        1 -> "Pad — Dark"
        2 -> "Pad — Light"
        3 -> "Pad — Mono"
        else -> "Pad — Auto"
    }

    /** Short caption under the tile. */
    fun caption(index: Int): String = when (index) {
        1 -> "Always dark"
        2 -> "Always light"
        3 -> "Themed"
        else -> "Follows system"
    }

    /** Enables [index] first, then disables the others, so the launcher never shows a gap. */
    fun set(ctx: Context, index: Int) {
        val i = index.coerceIn(0, COUNT - 1)
        val pm = ctx.packageManager
        val pkg = ctx.packageName
        runCatching {
            pm.setComponentEnabledSetting(
                ComponentName(pkg, "$pkg.${ALIASES[i]}"),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            ALIASES.forEachIndexed { k, alias ->
                if (k != i) pm.setComponentEnabledSetting(
                    ComponentName(pkg, "$pkg.$alias"),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, i).apply()
    }
}

/**
 * Light / dark / follow-the-system for the app itself.
 *
 * The in-app palette is accent-driven on a warm near-black base, so "Light" does
 * not repaint every surface — it switches the chrome that has a light variant
 * (window background, status bar icon colour, and the settings previews) and it
 * is the mode the "Pad — Auto" icon would be showing anyway, so the two never
 * disagree.
 */
object ThemeMode {
    const val SYSTEM = 0
    const val DARK = 1
    const val LIGHT = 2

    private const val PREFS = "gamershub"
    private const val KEY = "themeMode"

    fun current(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, SYSTEM).coerceIn(0, 2)

    fun set(ctx: Context, mode: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY, mode.coerceIn(0, 2)).apply()
    }

    fun label(mode: Int): String = when (mode) {
        DARK -> "Dark"
        LIGHT -> "Light"
        else -> "System"
    }
}
