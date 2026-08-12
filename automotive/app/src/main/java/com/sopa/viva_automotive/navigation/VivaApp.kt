package com.sopa.viva_automotive.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sopa.viva_automotive.R
import com.sopa.viva_automotive.core.ui.theme.VivaDimens
import com.sopa.viva_automotive.feature.hvac.HvacScreen
import com.sopa.viva_automotive.feature.media.MediaScreen
import com.sopa.viva_automotive.feature.media.domain.MediaSource
import com.sopa.viva_automotive.feature.settings.SettingsScreen
import com.sopa.viva_automotive.feature.voice.presentation.VoiceOverlay
import com.sopa.viva_automotive.home.HomeScreen

private enum class VivaDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    HOME("home", R.string.nav_home, Icons.Default.Home),
    HVAC("hvac", R.string.nav_climate, Icons.Default.Thermostat),
    MEDIA("media", R.string.nav_media, Icons.Default.MusicNote),
    RADIO("radio", R.string.nav_radio, Icons.Default.Radio),
    STATUS("status", R.string.nav_vehicle, Icons.Default.DirectionsCar),
    SETTINGS("settings", R.string.nav_settings, Icons.Default.Settings),
}

@Composable
fun VivaApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    fun goTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(112.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 16.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VivaDestination.entries.forEach { destination ->
                val label = stringResource(destination.labelRes)
                VivaNavRailItem(
                    selected = currentRoute == destination.route,
                    onClick = { goTo(destination.route) },
                    icon = destination.icon,
                    label = label,
                )
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = VivaDestination.HOME.route,
                modifier = Modifier.weight(1f),
            ) {
                composable(VivaDestination.HOME.route) {
                    HomeScreen(
                        onOpenMedia = { goTo(VivaDestination.MEDIA.route) },
                        onOpenRadio = { goTo(VivaDestination.RADIO.route) },
                    )
                }
                composable(VivaDestination.HVAC.route) { HvacScreen() }
                composable(VivaDestination.MEDIA.route) {
                    MediaScreen(source = MediaSource.LIBRARY)
                }
                composable(VivaDestination.RADIO.route) {
                    MediaScreen(source = MediaSource.RADIO)
                }
                composable(VivaDestination.STATUS.route) { VehicleHubScreen() }
                composable(VivaDestination.SETTINGS.route) { SettingsScreen() }
            }

            VoiceOverlay(modifier = Modifier.padding(16.dp))
        }
    }
}

/** Selected highlight wraps both icon and label (Material rail only paints the icon). */
@Composable
private fun VivaNavRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier
            .widthIn(min = VivaDimens.TouchTarget)
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = content,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
