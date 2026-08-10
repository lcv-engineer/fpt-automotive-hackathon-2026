package com.sopa.viva_automotive.feature.media.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sopa.viva_automotive.feature.media.R

/** Always rendered as a square (1:1), cropped to fill — never stretched. */
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
    Box(
        modifier = modifier
            .size(size)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.media_artwork),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.album_art_default),
                contentDescription = stringResource(R.string.media_artwork),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

fun formatDurationMs(durationMs: Long?): String? {
    if (durationMs == null || durationMs < 0L) return null
    val totalSec = durationMs / 1000L
    val minutes = totalSec / 60L
    val seconds = totalSec % 60L
    return "%d:%02d".format(minutes, seconds)
}
