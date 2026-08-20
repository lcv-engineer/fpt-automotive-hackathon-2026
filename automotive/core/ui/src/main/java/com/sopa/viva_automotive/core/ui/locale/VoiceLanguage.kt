package com.sopa.viva_automotive.core.ui.locale

enum class VoiceLanguage(
    val storageKey: String,
    /** Thư mục model Vosk trong `feature/voice/src/main/assets` cho ngôn ngữ này. */
    val voskAssetDir: String,
) {
    ENGLISH("en", "model-en-us"),
    VIETNAMESE("vi", "model-vi"),
    ;

    companion object {
        fun fromStorageKey(key: String?): VoiceLanguage =
            entries.firstOrNull { it.storageKey == key } ?: VIETNAMESE
    }
}
