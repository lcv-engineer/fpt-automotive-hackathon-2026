package com.sopa.viva_automotive.feature.voice.via

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.sopa.viva_automotive.feature.voice.R
import com.viva.voice.audio.AndroidPcmSource
import com.viva.voice.audio.AudioConfig
import com.viva.voice.hotword.HotwordConstants
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Software-path enrollment: record a short “Viva ơi” template for
 * [SoftwareHotwordEngine]. DSP enrollment uses the system enroll intent when
 * SoundTrigger reports KEYPHRASE_UNENROLLED.
 */
@AndroidEntryPoint
class HotwordEnrollmentActivity : ComponentActivity() {

    @Inject lateinit var hotwordController: HotwordController

    private var recordJob: Job? = null
    private val captured = ArrayList<Short>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        val prompt = TextView(this).apply {
            text = getString(R.string.hotword_enroll_prompt) +
                "\n(${HotwordConstants.KEYPHRASE})"
            textSize = 20f
        }
        val start = Button(this).apply { text = getString(R.string.hotword_enroll_start) }
        val stop = Button(this).apply {
            text = getString(R.string.hotword_enroll_stop)
            isEnabled = false
        }
        start.setOnClickListener {
            start.isEnabled = false
            stop.isEnabled = true
            captured.clear()
            recordJob = lifecycleScope.launch(Dispatchers.IO) {
                val source = AndroidPcmSource(AudioConfig.DEFAULT)
                val buffer = ShortArray(AudioConfig.DEFAULT.chunkSamples)
                source.start()
                try {
                    while (isActive && captured.size < AudioConfig.DEFAULT.sampleRate * 2) {
                        val read = source.read(buffer)
                        if (read > 0) {
                            for (i in 0 until read) captured.add(buffer[i])
                        }
                    }
                } finally {
                    source.stop()
                }
            }
        }
        stop.setOnClickListener {
            recordJob?.cancel()
            recordJob = null
            start.isEnabled = true
            stop.isEnabled = false
            lifecycleScope.launch {
                val pcm = withContext(Dispatchers.Default) { captured.toShortArray() }
                if (pcm.size < AudioConfig.DEFAULT.sampleRate / 4) {
                    Toast.makeText(this@HotwordEnrollmentActivity, "Too short", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                hotwordController.saveSoftwareTemplate(pcm)
                Toast.makeText(
                    this@HotwordEnrollmentActivity,
                    getString(R.string.hotword_enroll_saved),
                    Toast.LENGTH_SHORT,
                ).show()
                finish()
            }
        }
        root.addView(prompt)
        root.addView(start)
        root.addView(stop)
        setContentView(root)
    }

    override fun onDestroy() {
        recordJob?.cancel()
        super.onDestroy()
    }
}
