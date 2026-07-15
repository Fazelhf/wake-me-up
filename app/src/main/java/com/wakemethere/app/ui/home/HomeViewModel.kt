package com.wakemethere.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakemethere.app.data.repository.DestinationRepository
import com.wakemethere.app.domain.model.Destination
import com.wakemethere.app.domain.model.TrackingStatus
import com.wakemethere.app.service.TrackingService
import com.wakemethere.app.service.TrackingStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Home screen state: favorites list plus the live tracking status published
 * by the service.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: DestinationRepository,
    stateHolder: TrackingStateHolder,
) : ViewModel() {

    val favorites: StateFlow<List<Destination>> = repository.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trackingStatus: StateFlow<TrackingStatus> = stateHolder.status

    /** Arms the alarm for a saved favorite. */
    fun armFavorite(destination: Destination) {
        TrackingService.start(appContext, destination)
    }

    /** Cancels the currently armed alarm. */
    fun cancelTracking() {
        TrackingService.stop(appContext)
    }

    fun deleteFavorite(destination: Destination) {
        viewModelScope.launch { repository.delete(destination.id) }
    }
}
