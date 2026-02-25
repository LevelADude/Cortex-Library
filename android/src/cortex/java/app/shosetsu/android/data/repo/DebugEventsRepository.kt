package app.shosetsu.android.data.repo

import app.shosetsu.android.data.store.CortexDataStore
import app.shosetsu.android.domain.config.SourceConfigCodec
import app.shosetsu.android.domain.model.DebugEvent
import app.shosetsu.android.domain.model.DebugLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DebugEventsRepository(
    private val dataStore: CortexDataStore,
    private val maxSize: Int = 200
) {
    private val json = SourceConfigCodec.json
    private val scope = CoroutineScope(Dispatchers.IO)

    private val ring = DebugRingBuffer<DebugEvent>(maxSize)
    private val _events = MutableStateFlow<List<DebugEvent>>(emptyList())
    val events: StateFlow<List<DebugEvent>> = _events.asStateFlow()

    val persistLogs = dataStore.persistDebugLogs().stateIn(scope, SharingStarted.Eagerly, false)

    init {
        scope.launch {
            val initial = dataStore.debugLogsJson().first()?.let {
                runCatching { json.decodeFromString<List<DebugEvent>>(it) }.getOrNull().orEmpty()
            }.orEmpty()
            initial.takeLast(maxSize).forEach { ring.add(it) }
            _events.value = ring.asList()
        }
    }

    fun log(
        level: DebugLevel,
        category: String,
        message: String,
        sourceId: String? = null,
        sourceName: String? = null,
        details: String? = null
    ) {
        val event = DebugEvent(
            level = level,
            category = category,
            sourceId = sourceId,
            sourceName = sourceName,
            message = message,
            details = details
        )
        ring.add(event)
        val updated = ring.asList()
        _events.value = updated
        if (persistLogs.value) {
            scope.launch { dataStore.saveDebugLogsJson(json.encodeToString(updated)) }
        }
    }

    fun setPersistLogs(enabled: Boolean) {
        scope.launch {
            dataStore.savePersistDebugLogs(enabled)
            if (enabled) {
                dataStore.saveDebugLogsJson(json.encodeToString(_events.value))
            } else {
                dataStore.clearDebugLogs()
            }
        }
    }

    fun clear() {
        ring.clear()
        _events.value = emptyList()
        scope.launch { dataStore.clearDebugLogs() }
    }
}
