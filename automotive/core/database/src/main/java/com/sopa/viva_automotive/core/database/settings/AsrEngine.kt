package com.sopa.viva_automotive.core.database.settings

/**
 * Runtime speech-to-text backend. Default stays [VIVA] (local `viva-asr` container).
 * [GOOGLE] uses Cloud Speech-to-Text with a service-account JSON shipped only in
 * mock/debug secrets assets — never commit that file.
 */
enum class AsrEngine(val storageKey: String) {
    VIVA("viva"),
    GOOGLE("google"),
    ;

    companion object {
        fun fromStorageKey(key: String?): AsrEngine =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: VIVA
    }
}
