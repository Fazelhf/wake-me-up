package com.wakemethere.app.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakemethere.app.data.repository.TripRepository
import com.wakemethere.app.domain.model.Trip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Filter for the trip history list. */
enum class TripFilter { ALL, METRO, BRT }

/** Backs the Trip History list and single Trip Summary lookups. */
@HiltViewModel
class TripsViewModel @Inject constructor(
    private val repository: TripRepository,
) : ViewModel() {

    private val allTrips: StateFlow<List<Trip>> = repository.observeTrips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filter = MutableStateFlow(TripFilter.ALL)

    /** The trips visible under the current filter. */
    val trips: StateFlow<List<Trip>> = allTrips

    fun setFilter(f: TripFilter) {
        filter.value = f
    }

    fun visible(list: List<Trip>, f: TripFilter): List<Trip> = when (f) {
        TripFilter.ALL -> list
        TripFilter.METRO -> list.filter { it.transitType == "METRO" }
        TripFilter.BRT -> list.filter { it.transitType == "BRT" }
    }

    fun delete(trip: Trip) {
        viewModelScope.launch { repository.delete(trip.id) }
    }

    /** One-shot lookup for the summary screen. */
    suspend fun tripById(id: Long): Trip? = repository.getById(id)
}
