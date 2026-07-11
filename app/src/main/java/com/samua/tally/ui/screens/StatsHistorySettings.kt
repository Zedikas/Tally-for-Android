@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.samua.tally.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.samua.tally.data.TallyStore
import com.samua.tally.model.*
import com.samua.tally.ui.*
import com.samua.tally.ui.theme.colorFromRaw
import com.samua.tally.util.AppIconManager
import com.samua.tally.util.appIconChoices
import kotlin.math.max

@Composable
fun StatsScreen(store: TallyStore, contentPadding: PaddingValues) {
    val state by store.state.collectAsState()
    val active = state.counters.filterNot { it.isArchived }
    val total = active.sumOf { it.value }
    val completedGoals = active.count { it.goal != null && it.value >= it.goal }
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(title = { Text("Stats", fontWeight = FontWeight.Black) })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Total", total.toString(), Icons.Rounded.Calculate, Modifier.weight(1f))
                    StatCard("Goals", completedGoals.toString(), Icons.Rounded.EmojiEvents, Modifier.weight(1f))
                    StatCard("Actions", state.history.size.toString(), Icons.Rounded.Bolt, Modifier.weight(1f))
                }
            }
            item { SectionTitle("Counter values") }
            item { CounterBarChart(active) }
            item { SectionTitle("Activity trend") }
            item { HistoryLineChart(state.history.take(40).reversed()) }
            item { SectionTitle("Overview") }
            item {
                TallyCard(Modifier.fillMaxWidth()) {
                    SummaryRow("Active counters", active.size.toString())
                    SummaryRow("Folders", state.folders.size.toString())
                    SummaryRow("Pinned", active.count { it.isPinned }.toString())
                    SummaryRow("Locked", active.count { it.isLocked }.toString())
                    SummaryRow("Active sessions", state.sessions.count { it.isActive }.toString())
                    SummaryRow("Completed sessions", state.sessions.count { !it.isActive }.toString())
                }
            }
        }
    }
}

@Composable
private fun CounterBarChart(counters: List<TallyCounter>) {
    TallyCard(Modifier.fillMaxWidth()) {
        if (counters.isEmpty()) Text("No counters yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else {
            val top = counters.sortedByDescending { it.value }.take(8)
            val maxValue = max(1, top.maxOf { it.value })
            top.forEach { counter ->
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row { Text(counter.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); Text(counter.value.toString(), fontWeight = FontWeight.Bold) }
                    LinearProgressIndicator(progress = { counter.value.toFloat() / maxValue.toFloat() }, modifier = Modifier.fillMaxWidth(), color = colorFromRaw(counter.colorName))
                }
            }
        }
    }
}

@Composable
private fun HistoryLineChart(history: List<TallyHistoryEntry>) {
    val color = MaterialTheme.colorScheme.primary
    TallyCard(Modifier.fillMaxWidth()) {
        if (history.size < 2) Text("More history is needed for a trend chart.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else Canvas(Modifier.fillMaxWidth().height(190.dp)) {
            val values = history.map { it.afterValue.toFloat() }
            val minV = values.minOrNull() ?: 0f
            val maxV = values.maxOrNull() ?: 1f
            val range = (maxV - minV).coerceAtLeast(1f)
            val stepX = size.width / (values.size - 1)
            var previous: Offset? = null
            values.forEachIndexed { index, value ->
                val point = Offset(index * stepX, size.height - ((value - minV) / range) * size.height)
                previous?.let { drawLine(color, it, point, strokeWidth = 6f, cap = StrokeCap.Round) }
                drawCircle(color, radius = 5f, center = point)
                previous = point
            }
        }
    }
}

@Composable
private fun SummaryRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth()) { Text(title); Spacer(Modifier.weight(1f)); Text(value, fontWeight = FontWeight.Bold) }
}

@Composable
fun HistoryScreen(store: TallyStore, contentPadding: PaddingValues) {
    val state by store.state.collectAsState()
    var search by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val filtered = state.history.filter { search.isBlank() || it.counterName.contains(search, true) || it.action.contains(search, true) }
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(title = { Text("History", fontWeight = FontWeight.Black) }, actions = { IconButton({ confirmClear = true }, enabled = state.history.isNotEmpty()) { Icon(Icons.Rounded.DeleteSweep, "Clear") } })
        OutlinedTextField(search, { search = it }, placeholder = { Text("Search history") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), singleLine = true)
        if (filtered.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No history", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { entry ->
                TallyCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.counterName, fontWeight = FontWeight.Bold)
                            Text(entry.action, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatAppleDate(entry.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(if (entry.delta > 0) "+${entry.delta}" else entry.delta.toString(), fontWeight = FontWeight.Black, color = if (entry.delta >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear all history?") }, text = { Text("This cannot be undone.") }, confirmButton = { Button({ store.clearHistory(); confirmClear = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Clear") } }, dismissButton = { TextButton({ confirmClear = false }) { Text("Cancel") } })
}

@Composable
fun SettingsScreen(store: TallyStore, contentPadding: PaddingValues) {
    val state by store.state.collectAsState()
    val context = LocalContext.current
    var customHex by remember(state.customAccentHex) { mutableStateOf(state.customAccentHex) }
    var customDialog by remember { mutableStateOf(false) }
    var changelog by remember { mutableStateOf(false) }
    var importRaw by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { writeText(context, it, store.exportBackupJson()); message = "Backup exported." }
    }
    val exportHistory = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { writeText(context, it, store.exportHistoryCsv()); message = "History CSV exported." }
    }
    val exportSessions = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { writeText(context, it, store.exportSessionsCsv()); message = "Sessions CSV exported." }
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        importRaw = uri?.let { readText(context, it) }
    }

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(title = { Text("Settings", fontWeight = FontWeight.Black) })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            TallyCard(Modifier.fillMaxWidth()) {
                Text("Theme", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TallyTheme.entries.forEach { theme ->
                        FilterChip(selected = state.theme == theme, onClick = { store.setTheme(theme) }, label = { Text(when (theme) { TallyTheme.SYSTEM -> "Default"; TallyTheme.LIGHT -> "Light"; TallyTheme.DARK -> "Dark"; TallyTheme.OLED -> "OLED" }) }, modifier = Modifier.weight(1f))
                    }
                }
                Text("Accent theme", fontWeight = FontWeight.Bold)
                ColorGrid(state.accentRaw, { store.setAccent(it) }, allowCustom = true, onCustom = { customDialog = true })
                if (state.accentRaw == "custom") Text("Custom #${state.customAccentHex}", color = colorFromRaw("custom:${state.customAccentHex}"), fontWeight = FontWeight.Bold)
            }

            Text("App Icon", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            TallyCard(Modifier.fillMaxWidth()) {
                appIconChoices.forEach { icon ->
                    val selected = state.selectedIcon == icon.key
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            store.setSelectedIcon(icon.key)
                            AppIconManager.apply(context, icon.key)
                            message = "App icon changed to ${icon.title}. Some launchers may refresh after a few seconds."
                        }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = RoundedCornerShape(15.dp), color = iconPreviewColor(icon.key), modifier = Modifier.size(54.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Numbers, null, tint = if (icon.key == "Pearl") Color.DarkGray else Color.White) } }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(icon.title, fontWeight = FontWeight.Bold); Text(icon.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Text("Counter Management", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            TallyCard(Modifier.fillMaxWidth()) {
                Text("Archived Counters", fontWeight = FontWeight.Bold)
                if (state.counters.none { it.isArchived }) Text("Archive empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.counters.filter { it.isArchived }.forEach { counter ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(iconForSymbol(counter.symbol), null, tint = colorFromRaw(counter.colorName)); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Text(counter.name, fontWeight = FontWeight.Bold); Text("Value ${counter.value}", style = MaterialTheme.typography.bodySmall) }
                        TextButton({ store.restoreCounter(counter) }) { Text("Restore") }
                        IconButton({ store.permanentlyDeleteCounter(counter) }) { Icon(Icons.Rounded.DeleteForever, "Delete", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }

            Text("Backup & Import", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            TallyCard(Modifier.fillMaxWidth()) {
                Button({ exportBackup.launch("Tally_Backup_1_7.json") }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.SaveAlt, null); Spacer(Modifier.width(8.dp)); Text("Create JSON Backup") }
                OutlinedButton({ exportHistory.launch("Tally_History.csv") }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.TableView, null); Spacer(Modifier.width(8.dp)); Text("Export History CSV") }
                OutlinedButton({ exportSessions.launch("Tally_Sessions.csv") }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.TableChart, null); Spacer(Modifier.width(8.dp)); Text("Export Sessions CSV") }
                OutlinedButton({ importBackup.launch(arrayOf("application/json", "text/plain")) }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.UploadFile, null); Spacer(Modifier.width(8.dp)); Text("Preview & Import Backup") }
                Text("The JSON field names and Apple-reference date values are compatible with Tally 1.7 backups from iOS.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text("About", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            TallyCard(Modifier.fillMaxWidth()) {
                SummaryRow("Version", "1.7 build 17")
                TextButton({ changelog = true }) { Text("Changelog") }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }

    if (customDialog) AlertDialog(onDismissRequest = { customDialog = false }, title = { Text("Custom Accent") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(Modifier.fillMaxWidth().height(70.dp), shape = RoundedCornerShape(20.dp), color = colorFromRaw("custom:$customHex")) {}
            OutlinedTextField(customHex, { customHex = it.uppercase().filter { c -> c in "0123456789ABCDEF" }.take(6) }, label = { Text("HEX") }, prefix = { Text("#") }, singleLine = true)
        }
    }, confirmButton = { Button({ store.setAccent("custom", customHex); customDialog = false }, enabled = customHex.length == 6) { Text("Use color") } }, dismissButton = { TextButton({ customDialog = false }) { Text("Cancel") } })

    importRaw?.let { raw ->
        AlertDialog(onDismissRequest = { importRaw = null }, title = { Text("Import Tally Backup") }, text = { Text("Replace your current data, or merge this backup with it?") }, confirmButton = {
            Button({ val result = store.importBackupJson(raw, true); message = result.fold({ "Backup imported." }, { "Import failed: ${it.message}" }); importRaw = null }) { Text("Replace") }
        }, dismissButton = {
            Row { TextButton({ val result = store.importBackupJson(raw, false); message = result.fold({ "Backup merged." }, { "Import failed: ${it.message}" }); importRaw = null }) { Text("Merge") }; TextButton({ importRaw = null }) { Text("Cancel") } }
        })
    }

    if (changelog) AlertDialog(onDismissRequest = { changelog = false }, title = { Text("Tally 1.7 for Android") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("• Independent folders and counters")
            Text("• Hold-to-drag folder organization")
            Text("• Folder presets and Quick Create")
            Text("• Sessions, history, stats, milestones, archive, locking, and automatic resets")
            Text("• Light, Dark, and true OLED themes")
            Text("• Custom colors and alternate launcher icons")
            Text("• iOS-compatible JSON backup and CSV exports")
        }
    }, confirmButton = { TextButton({ changelog = false }) { Text("Done") } })
}

private fun writeText(context: android.content.Context, uri: Uri, value: String) {
    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(value) }
}
private fun readText(context: android.content.Context, uri: Uri): String? = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
private fun iconPreviewColor(key: String): Color = when (key) {
    "ClassicBlue" -> Color(0xFF0A84FF)
    "NeonDark" -> Color(0xFF020713)
    "Glass" -> Color(0xFF9EDBFF)
    "Pearl" -> Color(0xFFF6EBDD)
    "Amber" -> Color(0xFFFF9F0A)
    "TechGreen" -> Color(0xFF214F23)
    "CosmicPurple" -> Color(0xFF9000FF)
    else -> Color(0xFFFF38C7)
}
