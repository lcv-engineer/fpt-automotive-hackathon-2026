package com.sopa.viva_automotive.feature.media.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.mediaFavoritesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "viva_media_favorites",
)

/**
 * Personal playlist as MediaStore / library track id references only
 * (no MP3 copies — files stay where the scanner found them).
 */
@Singleton
class MediaFavoritesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val TRACK_IDS = stringSetPreferencesKey("favorite_track_ids")
    }

    val favoriteIds: Flow<Set<String>> = context.mediaFavoritesDataStore.data.map { prefs ->
        prefs[Keys.TRACK_IDS].orEmpty()
    }

    suspend fun setFavoriteIds(ids: Set<String>) {
        context.mediaFavoritesDataStore.edit { prefs ->
            prefs[Keys.TRACK_IDS] = ids
        }
    }

    suspend fun toggle(trackId: String): Set<String> {
        var next = emptySet<String>()
        context.mediaFavoritesDataStore.edit { prefs ->
            val current = prefs[Keys.TRACK_IDS].orEmpty()
            next = if (trackId in current) current - trackId else current + trackId
            prefs[Keys.TRACK_IDS] = next
        }
        return next
    }
}
