package com.jm.reader.ui.strings

import com.jm.reader.data.session.SessionManager

/**
 * UI languages. The API server only accepts `lang` = "TW" | "CN" (the web app never sends "EN"),
 * so English maps to "CN" for server-side data while all UI chrome is localised locally.
 */
enum class UiLanguage(val apiLang: String, val code: String, val displayName: String) {
    ZH_CN("CN", "zh-CN", "简体中文"),
    ZH_TW("TW", "zh-TW", "繁體中文"),
    EN("CN", "en", "English"),
}

/** Persists and broadcasts the current UI language so the whole tree recomposes on change. */
class LanguageManager(private val session: SessionManager) {

    private val _language = kotlinx.coroutines.flow.MutableStateFlow(session.language)
    val language: kotlinx.coroutines.flow.StateFlow<UiLanguage> = _language

    init {
        _language.value = session.language
    }

    fun set(lang: UiLanguage) {
        if (session.language != lang) session.language = lang
        if (_language.value != lang) _language.value = lang
    }
}
