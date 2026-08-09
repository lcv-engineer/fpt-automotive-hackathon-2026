package com.sopa.viva_automotive.feature.media.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sopa.viva_automotive.feature.media.R

@Composable
fun AlbumArtwork(
    artworkBytes: ByteArray?,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
) {
    val bitmap = remember(artworkBytes) {
        artworkBytes
            ?.takeIf { it.isNotEmpty() }
            ?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.media_artwork),
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size),
        )
    } else {
        Image(
            painter = painterResource(R.drawable.album_art_default),
            contentDescription = stringResource(R.string.media_artwork),
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size),
        )
    }
}

fun formatDurationMs(durationMs: Long?): String? {
    if (durationMs == null || durationMs < 0L) return null
    val totalSec = durationMs / 1000L
    val minutes = totalSec / 60L
    val seconds = totalSec % 60L
    return "%d:%02d".format(minutes, seconds)
}
