package com.chanakya.app.ui.networkdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chanakya.core.data.model.NetworkDetail
import com.chanakya.core.data.repository.TrackerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Loads the cross-app view of a single tracker network.
 *
 * @param trackerRepository the read model.
 * @param networkId the catalogue id of the network to show.
 */
class NetworkDetailViewModel(
    trackerRepository: TrackerRepository,
    networkId: String,
) : ViewModel() {

    /**
     * Everything known about the network, or null while loading or if the catalogue does
     * not recognise the id.
     */
    val detail: StateFlow<NetworkDetail?> = trackerRepository.observeNetworkDetail(networkId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
