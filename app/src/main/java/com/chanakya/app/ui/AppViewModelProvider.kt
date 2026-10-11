package com.chanakya.app.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chanakya.app.di.AppContainer
import com.chanakya.app.ui.appdetail.AppDetailViewModel
import com.chanakya.app.ui.apps.AppsViewModel
import com.chanakya.app.ui.home.HomeViewModel
import com.chanakya.app.ui.settings.SettingsViewModel

/**
 * Factory helpers that build each ViewModel with its dependencies from the
 * [AppContainer]. Keeps ViewModels free of any Android framework types.
 */
object AppViewModelProvider {

    /** Factory for [HomeViewModel]. */
    fun home(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            HomeViewModel(
                trackerRepository = container.trackerRepository,
                vpnConnectionManager = container.vpnConnectionManager,
                settingsRepository = container.settingsRepository,
            )
        }
    }

    /** Factory for [AppsViewModel]. */
    fun apps(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { AppsViewModel(container.trackerRepository) }
    }

    /** Factory for [AppDetailViewModel]. */
    fun appDetail(container: AppContainer, packageName: String): ViewModelProvider.Factory = viewModelFactory {
        initializer { AppDetailViewModel(container.trackerRepository, packageName) }
    }

    /** Factory for [SettingsViewModel]. */
    fun settings(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            SettingsViewModel(
                settingsRepository = container.settingsRepository,
                vpnConnectionManager = container.vpnConnectionManager,
            )
        }
    }
}
