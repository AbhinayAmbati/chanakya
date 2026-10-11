package com.chanakya.app.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chanakya.core.data.model.AppUsageSummary
import com.chanakya.core.data.repository.TrackerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Exposes the ranked list of apps that contacted trackers.
 */
class AppsViewModel(
    trackerRepository: TrackerRepository,
) : ViewModel() {

    /** Every app with tracking activity, most active first. */
    val apps: StateFlow<List<AppUsageSummary>> = trackerRepository.observeAppSummaries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
}
