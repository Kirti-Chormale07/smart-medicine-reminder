package com.example.mediremind.util

import android.content.Context
import com.example.mediremind.data.Prefs
import java.util.Locale

/** Applies the language chosen in Settings to any Context (activities, receivers, TTS). */
object LocaleHelper {

    fun wrap(base: Context): Context {
        val code = Prefs(base).langCode()
        val locale = Locale(code)
        Locale.setDefault(locale)
        val config = base.resources.configuration
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /** True when this Context's locale no longer matches the language chosen in Settings. */
    fun isStale(context: Context): Boolean {
        val current = context.resources.configuration.locales[0].language
        return current != Prefs(context).langCode()
    }
}
