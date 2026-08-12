package com.sopa.viva_automotive.feature.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HardwareDiagnosticsViewModel @Inject constructor(
    private val repository: HardwareDiagnosticsRepository,
) : ViewModel() {

    val state: StateFlow<HardwareDiagnosticsState> = repository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HardwareDiagnosticsState())

    fun refreshDiagnosis() = repository.runDiagnosisRefresh()

    fun startHardwareScan() {
        if (state.value.scan.isScanning) return
        viewModelScope.launch {
            repository.beginScan()
            val steps = 24
            for (i in 1..steps) {
                delay(100L)
                val progress = i / steps.toFloat()
                val eta = ((steps - i) * 0.1f).toInt().coerceAtLeast(0)
                repository.updateScanProgress(progress, eta)
            }
            repository.finishScan()
        }
    }

    fun startOtaUpdate() = repository.startOtaDownload()
}
