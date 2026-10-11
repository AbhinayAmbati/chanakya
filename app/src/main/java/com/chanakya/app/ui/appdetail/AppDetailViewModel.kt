package com.chanakya.app.ui.appdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chanakya.core.data.repository.AppDetail
import com.chanakya.core.data.repository.TrackerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Loads everything known about a single app for its detail screen.
 *
 * @param trackerRepository the read model.
 * @param packageName the app to show.
 */
class AppDetailViewModel(
    trackerRepository: TrackerRepository,
    packageName: String,
) : ViewModel() {

    /** The detail payload, or null while it is first loading. */
    val detail: StateFlow<AppDetail?> = trackerRepository.observeAppDetail(packageName)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )
}
