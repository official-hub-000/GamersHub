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
