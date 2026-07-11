package com.samua.tally.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private const val APPLE_EPOCH_OFFSET_SECONDS = 978_307_200.0

fun nowAppleSeconds(): Double = System.currentTimeMillis() / 1000.0 - APPLE_EPOCH_OFFSET_SECONDS
fun appleSecondsToEpochMillis(value: Double): Long = ((value + APPLE_EPOCH_OFFSET_SECONDS) * 1000.0).toLong()
fun formatAppleDate(value: Double, pattern: String = "MMM d, yyyy • HH:mm"): String =
    DateTimeFormatter.ofPattern(pattern)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(appleSecondsToEpochMillis(value)))

@Serializable
enum class ResetReminder {
    @SerialName("none") NONE,
    @SerialName("daily") DAILY,
    @SerialName("weekly") WEEKLY,
    @SerialName("monthly") MONTHLY;

    val title: String get() = when (this) {
        NONE -> "None"
        DAILY -> "Daily"
        WEEKLY -> "Weekly"
        MONTHLY -> "Monthly"
    }
}

@Serializable
enum class TallyTheme {
    @SerialName("system") SYSTEM,
    @SerialName("light") LIGHT,
    @SerialName("dark") DARK,
    @SerialName("oled") OLED
}

@Serializable
data class TallyCounter(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val value: Int = 0,
    val goal: Int? = null,
    val group: String = "",
    val symbol: String = "number.square.fill",
    val colorName: String = "blue",
    val notes: String = "",
    val createdAt: Double = nowAppleSeconds(),
    val updatedAt: Double = nowAppleSeconds(),
    val isArchived: Boolean = false,
    val stepValues: List<Int> = listOf(1, 5, 10),
    val resetReminder: ResetReminder = ResetReminder.NONE,
    val isPinned: Boolean = false,
    val isLocked: Boolean = false,
    val automaticResetEnabled: Boolean = false,
    val lastAutomaticResetAt: Double? = null,
    val milestones: List<Int> = listOf(10, 50, 100),
    val reachedMilestones: List<Int> = emptyList(),
    val folderColorName: String = "gray"
) {
    val displayGroup: String get() = group.trim().ifEmpty { "Unfiled" }
    val progress: Float? get() = goal?.takeIf { it > 0 }?.let { (value.toFloat() / it).coerceIn(0f, 1f) }
}

@Serializable
data class TallyFolder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorRaw: String = "blue",
    val defaultCounterColorRaw: String = "blue",
    val defaultSymbol: String = "number.square.fill",
    val defaultStepValues: List<Int> = listOf(1, 5, 10),
    val defaultResetReminder: ResetReminder = ResetReminder.NONE,
    val defaultAutomaticReset: Boolean = false,
    val createdAt: Double = nowAppleSeconds()
)

@Serializable
data class TallyHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val counterID: String,
    val counterName: String,
    val action: String,
    val delta: Int,
    val beforeValue: Int,
    val afterValue: Int,
    val date: Double = nowAppleSeconds()
)

@Serializable
data class TallySession(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val counterID: String? = null,
    val counterName: String = "Standalone",
    val startedAt: Double = nowAppleSeconds(),
    val endedAt: Double? = null,
    val startValue: Int = 0,
    val endValue: Int? = null,
    val notes: String = ""
) {
    val isActive: Boolean get() = endedAt == null
    val durationSeconds: Long get() {
        val end = endedAt ?: nowAppleSeconds()
        return (end - startedAt).coerceAtLeast(0.0).toLong()
    }
    val delta: Int? get() = endValue?.minus(startValue)
}

@Serializable
data class TallyState(
    val version: String = "1.7",
    val counters: List<TallyCounter> = sampleCounters(),
    val folders: List<TallyFolder> = sampleFolders(),
    val history: List<TallyHistoryEntry> = emptyList(),
    val sessions: List<TallySession> = emptyList(),
    val theme: TallyTheme = TallyTheme.SYSTEM,
    val accentRaw: String = "blue",
    val customAccentHex: String = "FF7EFF",
    val selectedIcon: String = "ClassicBlue"
)

@Serializable
data class TallyBackup(
    val version: String = "1.7",
    val exportedAt: Double = nowAppleSeconds(),
    val counters: List<TallyCounter> = emptyList(),
    val history: List<TallyHistoryEntry> = emptyList(),
    val theme: TallyTheme = TallyTheme.SYSTEM,
    val sessions: List<TallySession> = emptyList(),
    val folders: List<TallyFolder> = emptyList(),
    val accentRaw: String = "blue",
    val customAccentHex: String = "FF7EFF",
    val selectedIcon: String = "ClassicBlue"
)

val presetColors: LinkedHashMap<String, Long> = linkedMapOf(
    "blue" to 0xFF0A84FFL,
    "purple" to 0xFF9000FFL,
    "pink" to 0xFFFF7EFFL,
    "green" to 0xFF30D158L,
    "orange" to 0xFFFF9F0AL,
    "red" to 0xFFFF453AL,
    "teal" to 0xFF40C8E0L,
    "gray" to 0xFF8E8E93L
)

fun normalizeHex(value: String): String {
    val clean = value.uppercase().filter { it in "0123456789ABCDEF" }
    return if (clean.length == 6) clean else "0A84FF"
}
fun customColorRaw(hex: String): String = "custom:${normalizeHex(hex)}"
fun rawColorHex(raw: String): String? = when {
    raw.startsWith("custom:") -> normalizeHex(raw.substringAfter("custom:"))
    presetColors.containsKey(raw) -> presetColors.getValue(raw).toString(16).takeLast(6).uppercase()
    else -> null
}

fun sanitizeSteps(values: List<Int>): List<Int> {
    val cleaned = values.filter { it > 0 }.map { it.coerceAtMost(9999) }.distinct().take(3)
    return if (cleaned.isEmpty()) listOf(1, 5, 10) else cleaned
}
fun sanitizeMilestones(values: List<Int>): List<Int> = values.filter { it > 0 }.map { it.coerceAtMost(9_999_999) }.distinct().sorted()

fun sampleFolders(): List<TallyFolder> = listOf(
    TallyFolder(name = "Today", colorRaw = "blue", defaultCounterColorRaw = "blue", defaultSymbol = "drop.fill", defaultStepValues = listOf(1, 2, 4), defaultResetReminder = ResetReminder.DAILY, defaultAutomaticReset = true),
    TallyFolder(name = "Fitness", colorRaw = "green", defaultCounterColorRaw = "green", defaultSymbol = "figure.strengthtraining.traditional", defaultStepValues = listOf(1, 10, 25), defaultResetReminder = ResetReminder.WEEKLY),
    TallyFolder(name = "Focus", colorRaw = "purple", defaultCounterColorRaw = "purple", defaultSymbol = "book.fill", defaultStepValues = listOf(1, 5, 10))
)
fun sampleCounters(): List<TallyCounter> = listOf(
    TallyCounter(name = "Water", value = 3, goal = 8, group = "Today", symbol = "drop.fill", colorName = "blue", notes = "Daily glasses", stepValues = listOf(1, 2, 4), resetReminder = ResetReminder.DAILY, automaticResetEnabled = true, milestones = listOf(8, 30, 100), folderColorName = "blue"),
    TallyCounter(name = "Push-ups", value = 25, goal = 100, group = "Fitness", symbol = "figure.strengthtraining.traditional", colorName = "green", stepValues = listOf(1, 10, 25), resetReminder = ResetReminder.WEEKLY, milestones = listOf(50, 100, 500), folderColorName = "green"),
    TallyCounter(name = "Study reps", value = 12, group = "Focus", symbol = "book.fill", colorName = "purple", stepValues = listOf(1, 5, 10), milestones = listOf(25, 50, 100), folderColorName = "purple")
)

val symbolOptions = listOf(
    "number.square.fill" to "Counter",
    "checkmark.seal.fill" to "Completed",
    "drop.fill" to "Water",
    "flame.fill" to "Streak",
    "bolt.fill" to "Energy",
    "book.fill" to "Reading",
    "cart.fill" to "Shopping",
    "gamecontroller.fill" to "Gaming",
    "figure.strengthtraining.traditional" to "Workout",
    "star.fill" to "Favorite",
    "shippingbox.fill" to "Inventory",
    "calendar" to "Calendar",
    "trophy.fill" to "Achievement",
    "timer" to "Timer",
    "list.bullet.clipboard.fill" to "Checklist",
    "dollarsign.circle.fill" to "Money",
    "pills.fill" to "Medication",
    "cup.and.saucer.fill" to "Drinks",
    "figure.walk" to "Steps",
    "graduationcap.fill" to "Study"
)
