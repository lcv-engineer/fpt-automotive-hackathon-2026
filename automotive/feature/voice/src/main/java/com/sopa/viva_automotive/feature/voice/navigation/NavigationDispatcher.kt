package com.sopa.viva_automotive.feature.voice.navigation

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * App-scoped bus: voice emits a destination after a turn; [com.sopa.viva_automotive.navigation.VivaApp]
 * collects and drives the main [NavHost].
 */
@Singleton
class NavigationDispatcher @Inject constructor() {

    private val _requests = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val requests: SharedFlow<String> = _requests.asSharedFlow()

    fun navigateTo(route: String) {
        _requests.tryEmit(route)
    }
}
