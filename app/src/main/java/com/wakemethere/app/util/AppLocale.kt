package com.wakemethere.app.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Per-app language handling. The app defaults to Persian; the user can switch
 * to English in Settings. Choices persist via AppCompat's autoStoreLocales
 * (see the manifest service) and, on Android 13+, the system LocaleManager.
 */
object AppLocale {

    const val PERSIAN = "fa"
    const val ENGLISH = "en"

    /** BCP-47 tag of the current app language, defaulting to Persian. */
    fun current(): String =
        AppCompatDelegate.getApplicationLocales().toLanguageTags()
            .takeIf { it.isNotBlank() }
            ?.substringBefore('-')
            ?: PERSIAN

    fun set(languageTag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
    }

    /** Applies the Persian default the first time the app runs. */
    fun applyDefaultIfUnset() {
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            set(PERSIAN)
        }
    }
}
