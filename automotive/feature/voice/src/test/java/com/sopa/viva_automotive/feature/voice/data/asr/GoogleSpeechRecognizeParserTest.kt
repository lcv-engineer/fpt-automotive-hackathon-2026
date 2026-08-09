package com.sopa.viva_automotive.feature.voice.data.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleSpeechRecognizeParserTest {

    @Test
    fun `parses transcript and confidence`() {
        val parsed = GoogleSpeechRecognizeParser.parse(
            """
            {
              "results": [{
                "alternatives": [{
                  "transcript": "hạ điều hòa xuống 22 độ",
                  "confidence": 0.91
                }]
              }]
            }
            """.trimIndent(),
        )
        assertEquals("hạ điều hòa xuống 22 độ", parsed.text)
        assertEquals(0.91f, parsed.confidence!!, 0.001f)
    }

    @Test
    fun `empty results yield blank transcript`() {
        val parsed = GoogleSpeechRecognizeParser.parse("""{"results":[]}""")
        assertEquals("", parsed.text)
        assertNull(parsed.confidence)
    }
}
