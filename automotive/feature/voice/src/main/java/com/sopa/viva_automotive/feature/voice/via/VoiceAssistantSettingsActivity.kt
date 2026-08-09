package com.sopa.viva_automotive.feature.voice.via

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.sopa.viva_automotive.core.database.settings.SettingsDataStore
import com.sopa.viva_automotive.feature.voice.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class VoiceAssistantSettingsActivity : ComponentActivity() {

    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var hotwordController: HotwordController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        val title = TextView(this).apply {
            text = getString(R.string.voice_assistant_settings_title)
            textSize = 24f
        }
        val status = TextView(this)
        val toggle = Switch(this).apply {
            text = getString(R.string.hotword_settings_enable)
        }
        val hint = TextView(this).apply {
            text = getString(R.string.hotword_settings_enable_hint)
        }
        val enroll = Button(this).apply {
            text = getString(R.string.hotword_settings_enroll)
            setOnClickListener {
                val dspIntent = hotwordController.enrollIntentFromDsp()
                if (dspIntent != null) {
                    startActivity(dspIntent)
                } else {
                    startActivity(Intent(this@VoiceAssistantSettingsActivity, HotwordEnrollmentActivity::class.java))
                }
            }
        }
        root.addView(title)
        root.addView(status)
        root.addView(toggle)
        root.addView(hint)
        root.addView(enroll)
        setContentView(root)

        lifecycleScope.launch {
            settingsDataStore.settings.collectLatest { settings ->
                toggle.setOnCheckedChangeListener(null)
                toggle.isChecked = settings.hotwordEnabled
                toggle.setOnCheckedChangeListener { _, checked ->
                    lifecycleScope.launch { settingsDataStore.setHotwordEnabled(checked) }
                }
            }
        }
        lifecycleScope.launch {
            hotwordController.status.collectLatest { value ->
                status.text = getString(R.string.hotword_settings_status, value)
            }
        }
    }
}
