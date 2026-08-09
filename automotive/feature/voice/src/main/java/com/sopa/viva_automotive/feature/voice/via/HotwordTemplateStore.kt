package com.sopa.viva_automotive.feature.voice.via

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object HotwordTemplateStore {
    fun templateFile(context: Context): File =
        File(context.filesDir, "hotword/software_template_vi_vi_oi.pcm")

    fun save(context: Context, pcm16: ShortArray) {
        val file = templateFile(context)
        file.parentFile?.mkdirs()
        val buffer = ByteBuffer.allocate(pcm16.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asShortBuffer().put(pcm16)
        file.writeBytes(buffer.array())
    }

    fun load(context: Context): ShortArray? {
        val file = templateFile(context)
        if (!file.isFile || file.length() < 2) return null
        val bytes = file.readBytes()
        val samples = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
        return samples
    }
}
