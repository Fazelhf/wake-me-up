package com.wakemethere.app.util

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Bulletproof per-app language handling. The chosen language is stored in
 * SharedPreferences (synchronous read) and applied by wrapping every
 * activity's base context in [wrap] — so the very first frame is already in
 * the right language. Defaults to Persian.
 *
 * AppCompat's setApplicationLocales was applied too late on cold start
 * (the app came up in English until the user re-picked Persian); this
 * replaces it entirely.
 */
object AppLocale {

    const val PERSIAN = "fa"
    const val ENGLISH = "en"

    private const val PREFS = "app_locale"
    private const val KEY_LANG = "lang"

    fun current(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANG, PERSIAN) ?: PERSIAN

    /** Persists the language and recreates the activity to apply it. */
    fun set(context: Context, languageTag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANG, languageTag).commit()
        (context as? Activity)?.recreate()
    }

    /** Wraps [base] with the stored locale; call from attachBaseContext. */
    fun wrap(base: Context): Context {
        val locale = Locale(current(base))
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
