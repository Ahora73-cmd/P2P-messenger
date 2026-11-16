package com.example.p2pmessenger

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.p2pmessenger.settings.AppTexts
import com.example.p2pmessenger.settings.LocaleHelper
import com.example.p2pmessenger.settings.SettingsStore
import com.example.p2pmessenger.ui.ChatScreen
import com.example.p2pmessenger.ui.theme.P2PMessengerTheme

class MainActivity : ComponentActivity() {

    private lateinit var settings: SettingsStore

    override fun attachBaseContext(newBase: Context) {
        settings = SettingsStore(newBase)
        val language = settings.language.value
        AppTexts.bind(newBase, language)
        super.attachBaseContext(LocaleHelper.wrap(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by settings.theme.collectAsStateWithLifecycle()
            val language by settings.language.collectAsStateWithLifecycle()

            P2PMessengerTheme(themeMode = theme) {
                ChatScreen(
                    themeMode = theme,
                    language = language,
                    onThemeChange = settings::setTheme,
                    onLanguageChange = { chosen ->
                        if (chosen != language) {
                            settings.setLanguage(chosen)
                            AppTexts.bind(this, chosen)
                            recreate()
                        }
                    },
                )
            }
        }
    }
}
