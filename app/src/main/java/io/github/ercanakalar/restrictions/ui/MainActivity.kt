package io.github.ercanakalar.restrictions.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.ercanakalar.restrictions.R
import io.github.ercanakalar.restrictions.data.AppLanguage
import io.github.ercanakalar.restrictions.data.AppPrefs
import io.github.ercanakalar.restrictions.guard.ParentSession
import io.github.ercanakalar.restrictions.ui.theme.AppTheme

/** The parent screens. Every visit starts with the PIN. */
class MainActivity : ComponentActivity() {

    private var parentUnlocked by mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                ParentApp(
                    unlocked = parentUnlocked,
                    onUnlocked = { parentUnlocked = true },
                    onLock = {
                        parentUnlocked = false
                        finish()
                    },
                    openExternal = ::openExternal,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (ParentSession.consume()) parentUnlocked = true
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) parentUnlocked = false
    }

    /** Opens a system screen for the parent and lets them come back without the PIN. */
    private fun openExternal(intent: Intent) {
        ParentSession.grant()
        AppPrefs.get(this).startTamperGrace()
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            ParentSession.consume()
            Toast.makeText(this, R.string.error_no_settings_screen, Toast.LENGTH_LONG).show()
        }
    }
}
