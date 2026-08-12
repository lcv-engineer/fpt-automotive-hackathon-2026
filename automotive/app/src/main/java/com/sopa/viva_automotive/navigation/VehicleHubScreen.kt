package com.sopa.viva_automotive.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sopa.viva_automotive.R
import com.sopa.viva_automotive.core.ui.theme.VivaDimens
import com.sopa.viva_automotive.feature.diagnostics.HardwareDiagnosticsScreen
import com.sopa.viva_automotive.feature.vehiclestatus.VehicleStatusScreen

private enum class VehicleHubTab {
    STATUS,
    DIAGNOSTICS,
}

@Composable
fun VehicleHubScreen(modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableIntStateOf(VehicleHubTab.STATUS.ordinal) }
    val tabs = VehicleHubTab.entries

    Column(modifier = modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .height(VivaDimens.ButtonHeight),
        ) {
            tabs.forEachIndexed { index, tab ->
                SegmentedButton(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = tabs.size,
                    ),
                    label = {
                        Text(
                            text = stringResource(
                                when (tab) {
                                    VehicleHubTab.STATUS -> R.string.vehicle_tab_status
                                    VehicleHubTab.DIAGNOSTICS -> R.string.nav_diagnostics
                                },
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                )
            }
        }

        when (VehicleHubTab.entries[selectedTab]) {
            VehicleHubTab.STATUS -> VehicleStatusScreen(
                modifier = Modifier.weight(1f),
                showTitle = false,
            )
            VehicleHubTab.DIAGNOSTICS -> HardwareDiagnosticsScreen(
                modifier = Modifier.weight(1f),
                showTitle = false,
            )
        }
    }
}
