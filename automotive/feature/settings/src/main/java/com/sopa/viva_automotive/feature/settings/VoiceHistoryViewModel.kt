package com.sopa.viva_automotive.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sopa.viva_automotive.core.database.voicehistory.VoiceTurnHistoryEntity
import com.sopa.viva_automotive.core.database.voicehistory.VoiceTurnHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class VoiceHistoryViewModel @Inject constructor(
    private val repository: VoiceTurnHistoryRepository,
) : ViewModel() {

    val entries: StateFlow<List<VoiceTurnHistoryEntity>> = repository
        .observeRecent()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun clearHistory() {
        viewModelScope.launch { repository.clearAll() }
    }
}
