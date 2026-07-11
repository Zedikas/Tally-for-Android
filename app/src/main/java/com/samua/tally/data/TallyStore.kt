package com.samua.tally.data

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samua.tally.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID

private val Context.tallyDataStore by preferencesDataStore(name = "tally_state")
private val STATE_KEY = stringPreferencesKey("state_json")

class TallyStore(application: Application) : AndroidViewModel(application) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }
    private val _state = MutableStateFlow(TallyState())
    val state: StateFlow<TallyState> = _state.asStateFlow()
    private var loaded = false

    init {
        viewModelScope.launch {
            application.tallyDataStore.data
                .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
                .collect { prefs ->
                    val stored = prefs[STATE_KEY]
                    if (!loaded) {
                        val decoded = stored?.let { runCatching { json.decodeFromString<TallyState>(it) }.getOrNull() }
                        _state.value = migrate(decoded ?: TallyState())
                        loaded = true
                        performAutomaticResets()
                    }
                }
        }
    }

    private fun persist() {
        if (!loaded) return
        val snapshot = _state.value
        viewModelScope.launch {
            getApplication<Application>().tallyDataStore.edit { prefs ->
                prefs[STATE_KEY] = json.encodeToString(snapshot)
            }
        }
    }

    private fun mutate(block: (TallyState) -> TallyState) {
        _state.value = block(_state.value)
        persist()
    }

    private fun migrate(input: TallyState): TallyState {
        val normalizedCounters = input.counters.map { counter ->
            counter.copy(
                stepValues = sanitizeSteps(counter.stepValues),
                milestones = sanitizeMilestones(counter.milestones),
                reachedMilestones = counter.reachedMilestones.distinct().sorted(),
                folderColorName = counter.folderColorName.ifBlank { counter.colorName }
            )
        }
        val folders = if (input.folders.isNotEmpty()) input.folders else {
            normalizedCounters.map { it.group.trim() }.filter { it.isNotEmpty() }.distinct().map { name ->
                val sample = normalizedCounters.first { it.group.trim().equals(name, true) }
                TallyFolder(
                    name = name,
                    colorRaw = sample.folderColorName,
                    defaultCounterColorRaw = sample.colorName,
                    defaultSymbol = sample.symbol,
                    defaultStepValues = sample.stepValues,
                    defaultResetReminder = sample.resetReminder,
                    defaultAutomaticReset = sample.automaticResetEnabled
                )
            }
        }
        return input.copy(counters = normalizedCounters, folders = folders)
    }

    val activeCounters get() = state.value.counters.filterNot { it.isArchived }
    val archivedCounters get() = state.value.counters.filter { it.isArchived }
    val activeSessions get() = state.value.sessions.filter { it.isActive }.sortedByDescending { it.startedAt }
    val completedSessions get() = state.value.sessions.filterNot { it.isActive }.sortedByDescending { it.startedAt }

    fun setTheme(theme: TallyTheme) = mutate { it.copy(theme = theme) }
    fun setAccent(raw: String, customHex: String? = null) = mutate {
        it.copy(accentRaw = raw, customAccentHex = customHex?.let(::normalizeHex) ?: it.customAccentHex)
    }
    fun setSelectedIcon(icon: String) = mutate { it.copy(selectedIcon = icon) }

    fun addCounter(counter: TallyCounter): TallyCounter {
        val clean = normalizeCounter(counter.copy(name = counter.name.trim().ifEmpty { "New Counter" }))
        mutate { it.copy(counters = listOf(clean) + it.counters) }
        return clean
    }

    fun updateCounter(counter: TallyCounter) = mutate { state ->
        state.copy(counters = state.counters.map { if (it.id == counter.id) normalizeCounter(counter.copy(updatedAt = nowAppleSeconds())) else it })
    }

    fun duplicateCounter(counter: TallyCounter) {
        val names = state.value.counters.map { it.name.lowercase() }.toSet()
        var copyName = "${counter.name} Copy"
        var number = 2
        while (copyName.lowercase() in names) { copyName = "${counter.name} Copy $number"; number++ }
        addCounter(counter.copy(id = UUID.randomUUID().toString(), name = copyName, isPinned = false, isLocked = false, isArchived = false, reachedMilestones = emptyList(), createdAt = nowAppleSeconds(), updatedAt = nowAppleSeconds()))
    }

    fun archiveCounter(counter: TallyCounter) = updateCounter(counter.copy(isArchived = true))
    fun restoreCounter(counter: TallyCounter) = updateCounter(counter.copy(isArchived = false))
    fun permanentlyDeleteCounter(counter: TallyCounter) = mutate { state ->
        state.copy(
            counters = state.counters.filterNot { it.id == counter.id },
            history = state.history.filterNot { it.counterID == counter.id },
            sessions = state.sessions.filterNot { it.counterID == counter.id }
        )
    }

    fun togglePinned(counter: TallyCounter) = updateCounter(counter.copy(isPinned = !counter.isPinned))
    fun toggleLocked(counter: TallyCounter) = updateCounter(counter.copy(isLocked = !counter.isLocked))

    fun adjust(counterId: String, delta: Int) {
        val current = state.value.counters.firstOrNull { it.id == counterId } ?: return
        if (current.isLocked) return
        setValueInternal(current, current.value + delta, if (delta >= 0) "+$delta" else "$delta")
    }

    fun setExactValue(counterId: String, newValue: Int) {
        val current = state.value.counters.firstOrNull { it.id == counterId } ?: return
        if (current.isLocked || current.value == newValue) return
        setValueInternal(current, newValue, "Set to $newValue")
    }

    fun reset(counterId: String) {
        val current = state.value.counters.firstOrNull { it.id == counterId } ?: return
        if (current.isLocked || current.value == 0) return
        setValueInternal(current, 0, "Reset")
    }

    private fun setValueInternal(counter: TallyCounter, newValue: Int, action: String) {
        val before = counter.value
        val newlyReached = counter.milestones.filter { it > before && it <= newValue && it !in counter.reachedMilestones }
        val updated = counter.copy(
            value = newValue,
            updatedAt = nowAppleSeconds(),
            reachedMilestones = (counter.reachedMilestones + newlyReached).distinct().sorted()
        )
        val entries = buildList {
            add(TallyHistoryEntry(counterID = counter.id, counterName = counter.name, action = action, delta = newValue - before, beforeValue = before, afterValue = newValue))
            newlyReached.forEach { milestone ->
                add(TallyHistoryEntry(counterID = counter.id, counterName = counter.name, action = "Milestone $milestone", delta = 0, beforeValue = newValue, afterValue = newValue))
            }
        }
        mutate { state ->
            state.copy(
                counters = state.counters.map { if (it.id == counter.id) updated else it },
                history = entries + state.history
            )
        }
    }

    fun undoLastAction() {
        val entry = state.value.history.firstOrNull() ?: return
        mutate { state ->
            state.copy(
                counters = state.counters.map { if (it.id == entry.counterID) it.copy(value = entry.beforeValue, updatedAt = nowAppleSeconds()) else it },
                history = state.history.drop(1)
            )
        }
    }

    fun clearHistory() = mutate { it.copy(history = emptyList()) }

    fun createFolder(folder: TallyFolder): Boolean {
        val clean = folder.name.trim()
        if (clean.isEmpty() || state.value.folders.any { it.name.equals(clean, true) }) return false
        mutate { it.copy(folders = it.folders + folder.copy(name = clean, defaultStepValues = sanitizeSteps(folder.defaultStepValues))) }
        return true
    }

    fun updateFolder(folder: TallyFolder) {
        val old = state.value.folders.firstOrNull { it.id == folder.id } ?: return
        val clean = folder.name.trim()
        if (clean.isEmpty()) return
        mutate { state ->
            state.copy(
                folders = state.folders.map { if (it.id == folder.id) folder.copy(name = clean, defaultStepValues = sanitizeSteps(folder.defaultStepValues)) else it },
                counters = state.counters.map { counter ->
                    if (counter.group.equals(old.name, true)) counter.copy(group = clean, folderColorName = folder.colorRaw, updatedAt = nowAppleSeconds()) else counter
                }
            )
        }
    }

    fun deleteFolder(folder: TallyFolder, keepCounters: Boolean = true) = mutate { state ->
        val ids = state.counters.filter { it.group.equals(folder.name, true) }.map { it.id }.toSet()
        if (keepCounters) {
            state.copy(
                folders = state.folders.filterNot { it.id == folder.id },
                counters = state.counters.map { if (it.id in ids) it.copy(group = "", folderColorName = "gray", updatedAt = nowAppleSeconds()) else it }
            )
        } else {
            state.copy(
                folders = state.folders.filterNot { it.id == folder.id },
                counters = state.counters.filterNot { it.id in ids },
                history = state.history.filterNot { it.counterID in ids },
                sessions = state.sessions.filterNot { it.counterID in ids }
            )
        }
    }

    fun moveCounter(counterId: String, folderId: String?) {
        val folder = folderId?.let { id -> state.value.folders.firstOrNull { it.id == id } }
        mutate { state ->
            state.copy(counters = state.counters.map {
                if (it.id == counterId) it.copy(group = folder?.name.orEmpty(), folderColorName = folder?.colorRaw ?: "gray", updatedAt = nowAppleSeconds()) else it
            })
        }
    }

    fun quickCreate(folder: TallyFolder, name: String, startTimer: Boolean): TallyCounter? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        val counter = addCounter(TallyCounter(
            name = clean,
            group = folder.name,
            symbol = folder.defaultSymbol,
            colorName = folder.defaultCounterColorRaw,
            folderColorName = folder.colorRaw,
            stepValues = sanitizeSteps(folder.defaultStepValues),
            resetReminder = folder.defaultResetReminder,
            automaticResetEnabled = folder.defaultAutomaticReset
        ))
        if (startTimer) startSession(counter.id, clean, "")
        return counter
    }

    fun startSession(counterId: String?, title: String, notes: String) {
        val counter = counterId?.let { id -> state.value.counters.firstOrNull { it.id == id } }
        val cleanTitle = title.trim().ifEmpty { counter?.name ?: "Counting Session" }
        val session = TallySession(
            title = cleanTitle,
            counterID = counter?.id,
            counterName = counter?.name ?: "Standalone",
            startValue = counter?.value ?: 0,
            notes = notes.trim()
        )
        mutate { it.copy(sessions = listOf(session) + it.sessions) }
    }

    fun endSession(sessionId: String) {
        val session = state.value.sessions.firstOrNull { it.id == sessionId } ?: return
        val currentValue = session.counterID?.let { id -> state.value.counters.firstOrNull { it.id == id }?.value } ?: session.startValue
        mutate { state -> state.copy(sessions = state.sessions.map { if (it.id == sessionId) it.copy(endedAt = nowAppleSeconds(), endValue = currentValue) else it }) }
    }

    fun deleteSession(sessionId: String) = mutate { state -> state.copy(sessions = state.sessions.filterNot { it.id == sessionId }) }

    fun performAutomaticResets() {
        val now = nowAppleSeconds()
        val due = state.value.counters.filter { counter ->
            counter.automaticResetEnabled && counter.resetReminder != ResetReminder.NONE && !counter.isArchived && !counter.isLocked && isResetDue(counter.lastAutomaticResetAt ?: counter.updatedAt, now, counter.resetReminder)
        }
        if (due.isEmpty()) return
        mutate { state ->
            var history = state.history
            val counters = state.counters.map { counter ->
                if (due.none { it.id == counter.id }) counter else {
                    if (counter.value != 0) {
                        history = listOf(TallyHistoryEntry(counterID = counter.id, counterName = counter.name, action = "Automatic ${counter.resetReminder.title} reset", delta = -counter.value, beforeValue = counter.value, afterValue = 0)) + history
                    }
                    counter.copy(value = 0, updatedAt = now, lastAutomaticResetAt = now, reachedMilestones = emptyList())
                }
            }
            state.copy(counters = counters, history = history)
        }
    }

    private fun isResetDue(previousApple: Double, nowApple: Double, reminder: ResetReminder): Boolean {
        val zone = ZoneId.systemDefault()
        val previous = Instant.ofEpochMilli(appleSecondsToEpochMillis(previousApple)).atZone(zone)
        val current = Instant.ofEpochMilli(appleSecondsToEpochMillis(nowApple)).atZone(zone)
        return when (reminder) {
            ResetReminder.NONE -> false
            ResetReminder.DAILY -> previous.toLocalDate() != current.toLocalDate()
            ResetReminder.WEEKLY -> {
                val fields = WeekFields.of(Locale.getDefault())
                previous.get(fields.weekOfWeekBasedYear()) != current.get(fields.weekOfWeekBasedYear()) || previous.year != current.year
            }
            ResetReminder.MONTHLY -> previous.year != current.year || previous.monthValue != current.monthValue
        }
    }

    fun exportBackupJson(): String {
        val s = state.value
        return json.encodeToString(TallyBackup(
            counters = s.counters,
            folders = s.folders,
            history = s.history,
            sessions = s.sessions,
            theme = s.theme,
            accentRaw = s.accentRaw,
            customAccentHex = s.customAccentHex,
            selectedIcon = s.selectedIcon
        ))
    }

    fun importBackupJson(raw: String, replace: Boolean): Result<Unit> = runCatching {
        val backup = json.decodeFromString<TallyBackup>(raw)
        val importedFolders = if (backup.folders.isNotEmpty()) backup.folders else migrate(TallyState(counters = backup.counters, folders = emptyList())).folders
        if (replace) {
            _state.value = migrate(TallyState(
                version = "1.7",
                counters = backup.counters,
                folders = importedFolders,
                history = backup.history,
                sessions = backup.sessions,
                theme = backup.theme,
                accentRaw = backup.accentRaw,
                customAccentHex = backup.customAccentHex,
                selectedIcon = backup.selectedIcon
            ))
        } else {
            val counterMap = mutableMapOf<String, String>()
            val counters = backup.counters.map { old ->
                val id = UUID.randomUUID().toString(); counterMap[old.id] = id
                old.copy(id = id, name = uniqueName(old.name), createdAt = nowAppleSeconds(), updatedAt = nowAppleSeconds())
            }
            val folders = importedFolders.map { it.copy(id = UUID.randomUUID().toString(), name = uniqueFolderName(it.name), createdAt = nowAppleSeconds()) }
            val history = backup.history.map { it.copy(id = UUID.randomUUID().toString(), counterID = counterMap[it.counterID] ?: it.counterID) }
            val sessions = backup.sessions.map { it.copy(id = UUID.randomUUID().toString(), counterID = it.counterID?.let(counterMap::get)) }
            _state.value = state.value.copy(counters = counters + state.value.counters, folders = state.value.folders + folders, history = history + state.value.history, sessions = sessions + state.value.sessions)
        }
        persist()
    }

    fun exportHistoryCsv(): String = buildString {
        appendLine("Date,Counter,Action,Before,After,Delta")
        state.value.history.asReversed().forEach { e ->
            appendLine(listOf(formatAppleDate(e.date), e.counterName, e.action, e.beforeValue, e.afterValue, e.delta).joinToString(",") { csvEscape(it.toString()) })
        }
    }

    fun exportSessionsCsv(): String = buildString {
        appendLine("Title,Counter,Started,Ended,DurationSeconds,StartValue,EndValue,Delta,Notes")
        state.value.sessions.asReversed().forEach { s ->
            appendLine(listOf(s.title, s.counterName, formatAppleDate(s.startedAt), s.endedAt?.let { formatAppleDate(it) }.orEmpty(), s.durationSeconds, s.startValue, s.endValue ?: "", s.delta ?: "", s.notes).joinToString(",") { csvEscape(it.toString()) })
        }
    }

    private fun csvEscape(value: String) = "\"${value.replace("\"", "\"\"")}\""
    private fun normalizeCounter(counter: TallyCounter) = counter.copy(stepValues = sanitizeSteps(counter.stepValues), milestones = sanitizeMilestones(counter.milestones), reachedMilestones = counter.reachedMilestones.distinct().sorted())
    private fun uniqueName(base: String): String {
        val existing = state.value.counters.map { it.name.lowercase() }.toSet()
        var candidate = base; var n = 2
        while (candidate.lowercase() in existing) { candidate = "$base Imported $n"; n++ }
        return candidate
    }
    private fun uniqueFolderName(base: String): String {
        val existing = state.value.folders.map { it.name.lowercase() }.toSet()
        var candidate = base; var n = 2
        while (candidate.lowercase() in existing) { candidate = "$base Imported $n"; n++ }
        return candidate
    }
}
