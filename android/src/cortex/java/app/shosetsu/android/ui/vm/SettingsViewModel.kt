package app.shosetsu.android.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shosetsu.android.data.repo.DebugEventsRepository
import app.shosetsu.android.data.store.CortexDataStore
import app.shosetsu.android.domain.model.DebugEvent
import app.shosetsu.android.domain.model.DebugLevel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val dataStore: CortexDataStore,
    private val debugEventsRepository: DebugEventsRepository
) : ViewModel() {
    val events: StateFlow<List<DebugEvent>> = debugEventsRepository.events
    val persistLogs: StateFlow<Boolean> = debugEventsRepository.persistLogs

    fun setPersistLogs(enabled: Boolean) = debugEventsRepository.setPersistLogs(enabled)
    fun clearLogs() = debugEventsRepository.clear()

    fun resetSettings() = viewModelScope.launch {
        dataStore.resetSettings()
        debugEventsRepository.log(DebugLevel.Info, "settings", "Settings reset to defaults")
    }
}
