package com.sopa.viva_automotive.core.ui.locale

enum class AppLanguage(val storageKey: String, val languageTag: String?) {
    SYSTEM("system", null),
    ENGLISH("en", "en"),
    VIETNAMESE("vi", "vi"),
    ;

    companion object {
        fun fromStorageKey(key: String?): AppLanguage =
            entries.firstOrNull { it.storageKey == key } ?: VIETNAMESE
    }
}
