package com.example.p2pmessenger.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import com.example.p2pmessenger.R
import java.util.Locale

/** Per-app локаль без AppCompat и без перезапуска процесса. */
object LocaleHelper {

    fun wrap(context: Context, language: AppLanguage): Context {
        val locale = Locale.forLanguageTag(language.tag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}

/**
 * Строки для слоёв вне Compose (Bluetooth-сервис, ViewModel).
 * Держит ТОЛЬКО application context — утечек Activity нет.
 * Обновляется из MainActivity при старте и при смене языка.
 */
object AppTexts {

    @SuppressLint("StaticFieldLeak")
    private var localized: Context? = null

    fun bind(context: Context, language: AppLanguage) {
        localized = LocaleHelper.wrap(context.applicationContext, language)
    }

    fun get(@StringRes id: Int, vararg args: Any): String =
        localized?.getString(id, *args) ?: fallback(id)

    /** На случай вызова до первого bind. */
    private fun fallback(@StringRes id: Int): String = when (id) {
        R.string.peer_unknown -> "Device"
        R.string.device_unknown -> "Unnamed device"
        else -> ""
    }
}
