package com.viva.voice.hotword

import java.util.Locale

/** Canonical AOSP hotword keyphrase for VIVA. */
object HotwordConstants {
    const val KEYPHRASE = "Viva ơi"
    val LOCALE: Locale = Locale.forLanguageTag("vi-VN")
    const val LOCALE_TAG = "vi-VN"

    /** Folded text aliases accepted by the NLU wake stripper. */
    val TEXT_ALIASES = listOf("vi-vi oi", "vivi oi", "viva oi", "vi vi oi")
}
