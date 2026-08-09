package com.sopa.viva_automotive.core.ui.locale

enum class VoiceLanguage(
    val storageKey: String,
) {
    ENGLISH("en"),
    VIETNAMESE("vi"),
    ;

    companion object {
        fun fromStorageKey(key: String?): VoiceLanguage =
            entries.firstOrNull { it.storageKey == key } ?: VIETNAMESE
    }
}
