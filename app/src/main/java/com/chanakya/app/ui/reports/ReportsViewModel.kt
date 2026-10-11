package com.chanakya.app.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chanakya.core.data.model.ReportWindow
import com.chanakya.core.data.repository.ReportsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Backs the Reports screen with three aggregate windows.
 */
class ReportsViewModel(
    reportsRepository: ReportsRepository,
) : ViewModel() {

    /** Activity since midnight today. */
    val today: StateFlow<ReportWindow> = reportsRepository.observeToday()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportWindow.EMPTY)

    /** Activity over the last seven days. */
    val week: StateFlow<ReportWindow> = reportsRepository.observeWeek()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportWindow.EMPTY)

    /** Activity since the app started recording. */
    val allTime: StateFlow<ReportWindow> = reportsRepository.observeAllTime()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportWindow.EMPTY)
}
