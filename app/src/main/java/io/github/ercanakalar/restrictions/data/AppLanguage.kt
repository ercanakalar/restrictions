package io.github.ercanakalar.restrictions.data

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The app's own language setting. Android 13+ supports a language per app (also selectable in
 * the phone's settings); on older versions the chosen language is stored here and applied by
 * wrapping contexts with [wrap].
 */
object AppLanguage {

    /** Languages the app is translated into. */
    val SUPPORTED = listOf("en", "tr")

    /** The language chosen for the app, or null to follow the phone's language. */
    fun selected(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (locales.isEmpty) null else locales[0]?.language
        } else {
            AppPrefs.get(context).language
        }

    /** On Android 13+ the system recreates the activities; before that the caller must. */
    fun select(context: Context, tag: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            AppPrefs.get(context).language = tag
        }
    }

    /** [base] with the app language applied. A no-op on Android 13+, where the system does it. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = AppPrefs.get(base).language ?: return base
        return localized(base, tag)
    }

    fun localized(base: Context, tag: String): Context {
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(config)
    }
}
