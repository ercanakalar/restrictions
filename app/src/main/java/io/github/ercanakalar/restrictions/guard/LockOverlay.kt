package io.github.ercanakalar.restrictions.guard

import android.content.Context
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.ercanakalar.restrictions.core.LockDecision
import io.github.ercanakalar.restrictions.data.AppLanguage
import io.github.ercanakalar.restrictions.ui.LockScreen
import io.github.ercanakalar.restrictions.ui.LockScreenActions
import io.github.ercanakalar.restrictions.ui.theme.AppTheme

/**
 * The full-screen lock. It is an accessibility overlay window, which sits above all apps,
 * the status bar and the navigation bar, and stays put when Home or Recents is pressed.
 */
internal class LockOverlay(
    private val context: Context,
    private val actions: LockScreenActions,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val audioManager = context.getSystemService(AudioManager::class.java)

    // Taking audio focus pauses music and videos that would otherwise keep playing under the lock.
    private val audioFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build(),
        )
        .setOnAudioFocusChangeListener { }
        .build()

    private val decision = mutableStateOf<LockDecision.Locked?>(null)
    private var view: ComposeView? = null
    private var owner: OverlayLifecycleOwner? = null

    fun show(locked: LockDecision.Locked) {
        decision.value = locked
        if (view != null) return

        val newOwner = OverlayLifecycleOwner()
        val newView = ComposeView(AppLanguage.wrap(context)).apply {
            setViewTreeLifecycleOwner(newOwner)
            setViewTreeSavedStateRegistryOwner(newOwner)
            setContent {
                AppTheme {
                    decision.value?.let { LockScreen(it, actions) }
                }
            }
        }
        newOwner.resume()
        try {
            windowManager.addView(newView, layoutParams())
        } catch (e: RuntimeException) {
            Log.e(TAG, "Could not show the lock screen", e)
            newOwner.destroy()
            return
        }
        view = newView
        owner = newOwner
        audioManager.requestAudioFocus(audioFocus)
    }

    fun hide() {
        val current = view ?: return
        view = null
        try {
            windowManager.removeViewImmediate(current)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Lock screen was already gone", e)
        }
        owner?.destroy()
        owner = null
        audioManager.abandonAudioFocusRequest(audioFocus)
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.OPAQUE,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "FamilyLockOverlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private companion object {
        const val TAG = "LockOverlay"
    }
}

/** Compose needs a lifecycle and saved-state owner; a window outside an activity has neither. */
private class OverlayLifecycleOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    init {
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun resume() {
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
