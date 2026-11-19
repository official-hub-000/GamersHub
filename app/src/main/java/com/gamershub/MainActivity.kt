package com.gamershub

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.gamershub.data.AppRepo
import com.gamershub.ui.RootShell

class MainActivity : ComponentActivity() {

    /** Sheet requested from outside (floating panel button, shortcut, deep link). */
    private var openSheet by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // The saved light/dark choice has to land before the window is created,
        // otherwise the first frame is drawn in the system mode and then flips.
        runCatching { applyThemeMode() }
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        openSheet = intent?.getStringExtra(EXTRA_OPEN)
        val repo = AppRepo(applicationContext)
        // Bind the Shizuku user service immediately when the grant already exists.
        runCatching { com.gamershub.core.shizuku.Shell.ensure(this) }
        // Self-heal the launcher alias after an app update: exactly one must be enabled.
        runCatching { com.gamershub.data.AppIcon.set(this, com.gamershub.data.AppIcon.current(this)) }
        setContent {
            RootShell(repo, openSheet)
        }
    }

    /** Forces the window into the saved mode; SYSTEM leaves the app alone. */
    private fun applyThemeMode() {
        val mode = com.gamershub.data.ThemeMode.current(applicationContext)
        val wanted = when (mode) {
            com.gamershub.data.ThemeMode.DARK -> Configuration.UI_MODE_NIGHT_YES
            com.gamershub.data.ThemeMode.LIGHT -> Configuration.UI_MODE_NIGHT_NO
            else -> Configuration.UI_MODE_NIGHT_UNDEFINED
        }
        if (wanted != Configuration.UI_MODE_NIGHT_UNDEFINED &&
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != wanted
        ) {
            applyOverrideConfiguration(
                Configuration(resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or wanted
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-bind after returning from the Shizuku prompt / app switch.
        runCatching { com.gamershub.core.shizuku.Shell.ensure(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openSheet = intent.getStringExtra(EXTRA_OPEN)
    }

    companion object {
        const val EXTRA_OPEN = "open"
    }
}
