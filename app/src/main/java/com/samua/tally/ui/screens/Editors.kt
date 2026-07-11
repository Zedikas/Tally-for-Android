@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.samua.tally.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.samua.tally.data.TallyStore
import com.samua.tally.model.*
import com.samua.tally.ui.*
import com.samua.tally.ui.theme.colorFromRaw

private data class CounterTemplateAndroid(
    val title: String,
    val subtitle: String,
    val name: String,
    val goal: Int?,
    val symbol: String,
    val color: String,
    val steps: List<Int>,
    val reset: ResetReminder
)

private val counterTemplates = listOf(
    CounterTemplateAndroid("Simple Tally", "Basic count-up tracker", "New Counter", null, "number.square.fill", "blue", listOf(1, 5, 10), ResetReminder.NONE),
    CounterTemplateAndroid("Daily Goal", "Track progress toward a target", "Daily Goal", 10, "checkmark.seal.fill", "green", listOf(1, 2, 5), ResetReminder.DAILY),
    CounterTemplateAndroid("Water", "Glasses or bottles", "Water", 8, "drop.fill", "blue", listOf(1, 2, 4), ResetReminder.DAILY),
    CounterTemplateAndroid("Workout", "Sets and repetitions", "Workout Reps", 100, "figure.strengthtraining.traditional", "green", listOf(1, 10, 25), ResetReminder.WEEKLY),
    CounterTemplateAndroid("Reading", "Pages or chapters", "Reading", 50, "book.fill", "purple", listOf(1, 5, 10), ResetReminder.WEEKLY),
    CounterTemplateAndroid("Streak", "Days or consecutive wins", "Streak", null, "flame.fill", "red", listOf(1, 7, 30), ResetReminder.DAILY)
)

@Composable
fun CounterEditorDialog(store: TallyStore, existing: TallyCounter? = null, initialFolder: TallyFolder? = null, onDismiss: () -> Unit) {
    val appState by store.state.collectAsState()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var goal by remember { mutableStateOf(existing?.goal?.toString().orEmpty()) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var selectedFolderId by remember { mutableStateOf(initialFolder?.id ?: appState.folders.firstOrNull { it.name.equals(existing?.group, true) }?.id) }
    var symbol by remember { mutableStateOf(existing?.symbol ?: initialFolder?.defaultSymbol ?: "number.square.fill") }
    var colorRaw by remember { mutableStateOf(existing?.colorName ?: initialFolder?.defaultCounterColorRaw ?: "blue") }
    var step1 by remember { mutableStateOf(existing?.stepValues?.getOrNull(0)?.toString() ?: initialFolder?.defaultStepValues?.getOrNull(0)?.toString() ?: "1") }
    var step2 by remember { mutableStateOf(existing?.stepValues?.getOrNull(1)?.toString() ?: initialFolder?.defaultStepValues?.getOrNull(1)?.toString() ?: "5") }
    var step3 by remember { mutableStateOf(existing?.stepValues?.getOrNull(2)?.toString() ?: initialFolder?.defaultStepValues?.getOrNull(2)?.toString() ?: "10") }
    var reset by remember { mutableStateOf(existing?.resetReminder ?: initialFolder?.defaultResetReminder ?: ResetReminder.NONE) }
    var autoReset by remember { mutableStateOf(existing?.automaticResetEnabled ?: initialFolder?.defaultAutomaticReset ?: false) }
    var pinned by remember { mutableStateOf(existing?.isPinned ?: false) }
    var locked by remember { mutableStateOf(existing?.isLocked ?: false) }
    var milestones by remember { mutableStateOf(existing?.milestones?.joinToString(", ") ?: "10, 50, 100") }
    var folderMenu by remember { mutableStateOf(false) }
    var customColor by remember { mutableStateOf(false) }

    fun applyFolder(folder: TallyFolder?) {
        selectedFolderId = folder?.id
        if (existing == null && folder != null) {
            symbol = folder.defaultSymbol
            colorRaw = folder.defaultCounterColorRaw
            val values = sanitizeSteps(folder.defaultStepValues)
            step1 = values.getOrElse(0) { 1 }.toString()
            step2 = values.getOrElse(1) { 5 }.toString()
            step3 = values.getOrElse(2) { 10 }.toString()
            reset = folder.defaultResetReminder
            autoReset = folder.defaultAutomaticReset
        }
    }

    FullScreenDialog(title = if (existing == null) "New Counter" else "Edit Counter", onDismiss = onDismiss, confirmLabel = "Save", confirmEnabled = name.trim().isNotEmpty(), onConfirm = {
        val folder = appState.folders.firstOrNull { it.id == selectedFolderId }
        val values = sanitizeSteps(listOfNotNull(step1.toIntOrNull(), step2.toIntOrNull(), step3.toIntOrNull()))
        val milestoneValues = sanitizeMilestones(milestones.split(',').mapNotNull { it.trim().toIntOrNull() })
        if (existing == null) {
            store.addCounter(TallyCounter(
                name = name.trim(), goal = goal.toIntOrNull(), group = folder?.name.orEmpty(), symbol = symbol,
                colorName = colorRaw, folderColorName = folder?.colorRaw ?: "gray", notes = notes.trim(), stepValues = values,
                resetReminder = reset, automaticResetEnabled = autoReset && reset != ResetReminder.NONE,
                isPinned = pinned, isLocked = locked, milestones = milestoneValues
            ))
        } else {
            store.updateCounter(existing.copy(
                name = name.trim(), goal = goal.toIntOrNull(), group = folder?.name.orEmpty(), symbol = symbol,
                colorName = colorRaw, folderColorName = folder?.colorRaw ?: "gray", notes = notes.trim(), stepValues = values,
                resetReminder = reset, automaticResetEnabled = autoReset && reset != ResetReminder.NONE,
                isPinned = pinned, isLocked = locked, milestones = milestoneValues
            ))
        }
        onDismiss()
    }) {
        if (existing == null) {
            Text("Templates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            counterTemplates.forEach { template ->
                Surface(onClick = {
                    name = template.name; goal = template.goal?.toString().orEmpty(); symbol = template.symbol; colorRaw = template.color
                    step1 = template.steps.getOrElse(0) { 1 }.toString(); step2 = template.steps.getOrElse(1) { 5 }.toString(); step3 = template.steps.getOrElse(2) { 10 }.toString(); reset = template.reset
                }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(iconForSymbol(template.symbol), null, tint = colorFromRaw(template.color))
                        Spacer(Modifier.width(12.dp))
                        Column { Text(template.title, fontWeight = FontWeight.Bold); Text(template.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }

        EditorSection("Counter") {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Box {
                OutlinedButton(onClick = { folderMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    val folder = appState.folders.firstOrNull { it.id == selectedFolderId }
                    Icon(if (folder == null) Icons.Rounded.MoveToInbox else Icons.Rounded.Folder, null, tint = folder?.let { colorFromRaw(it.colorRaw) } ?: MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp)); Text(folder?.name ?: "Unfiled"); Spacer(Modifier.weight(1f)); Icon(Icons.Rounded.ExpandMore, null)
                }
                DropdownMenu(folderMenu, { folderMenu = false }) {
                    DropdownMenuItem(text = { Text("Unfiled") }, leadingIcon = { Icon(Icons.Rounded.MoveToInbox, null) }, onClick = { applyFolder(null); folderMenu = false })
                    appState.folders.forEach { folder ->
                        DropdownMenuItem(text = { Text(folder.name, color = colorFromRaw(folder.colorRaw)) }, leadingIcon = { Icon(Icons.Rounded.Folder, null, tint = colorFromRaw(folder.colorRaw)) }, onClick = { applyFolder(folder); folderMenu = false })
                    }
                }
            }
            OutlinedTextField(goal, { goal = it.filter { ch -> ch.isDigit() } }, label = { Text("Optional goal") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        }

        EditorSection("Style") {
            Text("Counter color", fontWeight = FontWeight.SemiBold)
            ColorGrid(colorRaw, { colorRaw = it }, onCustom = { customColor = true })
            Text("Symbol", fontWeight = FontWeight.SemiBold)
            SymbolGrid(symbol) { symbol = it }
        }

        EditorSection("Step buttons") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(step1 to { v: String -> step1 = v }, step2 to { v: String -> step2 = v }, step3 to { v: String -> step3 = v }).forEachIndexed { index, pair ->
                    OutlinedTextField(pair.first, { pair.second(it.filter(Char::isDigit)) }, label = { Text("Step ${index + 1}") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }
            }
        }

        EditorSection("Reset & safety") {
            ResetSelector(reset) { reset = it; if (it == ResetReminder.NONE) autoReset = false }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Reset automatically"); Spacer(Modifier.weight(1f)); Switch(autoReset, { autoReset = it }, enabled = reset != ResetReminder.NONE) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Pin as favorite"); Spacer(Modifier.weight(1f)); Switch(pinned, { pinned = it }) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Lock counter"); Spacer(Modifier.weight(1f)); Switch(locked, { locked = it }) }
            OutlinedTextField(milestones, { milestones = it }, label = { Text("Milestones") }, supportingText = { Text("Comma separated, for example 10, 50, 100") }, modifier = Modifier.fillMaxWidth())
        }
    }
    if (customColor) HexColorDialog("Counter color", colorRaw, { colorRaw = it; customColor = false }, { customColor = false })
}

@Composable
fun FolderEditorDialog(store: TallyStore, existing: TallyFolder? = null, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var folderColor by remember { mutableStateOf(existing?.colorRaw ?: "blue") }
    var counterColor by remember { mutableStateOf(existing?.defaultCounterColorRaw ?: "blue") }
    var symbol by remember { mutableStateOf(existing?.defaultSymbol ?: "number.square.fill") }
    var step1 by remember { mutableStateOf(existing?.defaultStepValues?.getOrNull(0)?.toString() ?: "1") }
    var step2 by remember { mutableStateOf(existing?.defaultStepValues?.getOrNull(1)?.toString() ?: "5") }
    var step3 by remember { mutableStateOf(existing?.defaultStepValues?.getOrNull(2)?.toString() ?: "10") }
    var reset by remember { mutableStateOf(existing?.defaultResetReminder ?: ResetReminder.NONE) }
    var autoReset by remember { mutableStateOf(existing?.defaultAutomaticReset ?: false) }
    var customFolder by remember { mutableStateOf(false) }
    var customCounter by remember { mutableStateOf(false) }

    FullScreenDialog(if (existing == null) "New Folder" else "Edit Folder", onDismiss, "Save", name.trim().isNotEmpty(), {
        val folder = (existing ?: TallyFolder(name = name)).copy(
            name = name.trim(), colorRaw = folderColor, defaultCounterColorRaw = counterColor, defaultSymbol = symbol,
            defaultStepValues = sanitizeSteps(listOfNotNull(step1.toIntOrNull(), step2.toIntOrNull(), step3.toIntOrNull())),
            defaultResetReminder = reset, defaultAutomaticReset = autoReset && reset != ResetReminder.NONE
        )
        if (existing == null) store.createFolder(folder) else store.updateFolder(folder)
        onDismiss()
    }) {
        EditorSection("Folder") { OutlinedTextField(name, { name = it }, label = { Text("Folder name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        EditorSection("Folder appearance") {
            Text("Folder color", fontWeight = FontWeight.SemiBold)
            ColorGrid(folderColor, { folderColor = it }, onCustom = { customFolder = true })
        }
        EditorSection("Quick Create presets") {
            Text("Default counter color", fontWeight = FontWeight.SemiBold)
            ColorGrid(counterColor, { counterColor = it }, onCustom = { customCounter = true })
            Text("Default symbol", fontWeight = FontWeight.SemiBold)
            SymbolGrid(symbol) { symbol = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(step1 to { v: String -> step1 = v }, step2 to { v: String -> step2 = v }, step3 to { v: String -> step3 = v }).forEachIndexed { index, pair ->
                    OutlinedTextField(pair.first, { pair.second(it.filter(Char::isDigit)) }, label = { Text("Step ${index + 1}") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }
            }
            ResetSelector(reset) { reset = it; if (it == ResetReminder.NONE) autoReset = false }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Reset automatically"); Spacer(Modifier.weight(1f)); Switch(autoReset, { autoReset = it }, enabled = reset != ResetReminder.NONE) }
            Text("The shortcut beside the folder can create a counter or create one and immediately start a linked timer.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (customFolder) HexColorDialog("Folder color", folderColor, { folderColor = it; customFolder = false }, { customFolder = false })
    if (customCounter) HexColorDialog("Counter color", counterColor, { counterColor = it; customCounter = false }, { customCounter = false })
}

@Composable
fun QuickCreateDialog(store: TallyStore, folder: TallyFolder, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AddCircle, null, tint = colorFromRaw(folder.colorRaw), modifier = Modifier.size(44.dp)) },
        title = { Text("Quick Create") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Uses the presets from ${folder.name}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button({ store.quickCreate(folder, name, false); onDismiss() }, enabled = name.trim().isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Numbers, null); Spacer(Modifier.width(8.dp)); Text("Create Counter") }
                OutlinedButton({ store.quickCreate(folder, name, true); onDismiss() }, enabled = name.trim().isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Timer, null); Spacer(Modifier.width(8.dp)); Text("Create & Start Timer") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ExactValueDialog(store: TallyStore, counter: TallyCounter, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(counter.value.toString()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(counter.name) }, text = {
        OutlinedTextField(value, { value = it.filter { ch -> ch.isDigit() || ch == '-' } }, label = { Text("Exact value") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
    }, confirmButton = { Button({ value.toIntOrNull()?.let { store.setExactValue(counter.id, it) }; onDismiss() }, enabled = value.toIntOrNull() != null && !counter.isLocked) { Text("Set") } }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
private fun FullScreenDialog(title: String, onDismiss: () -> Unit, confirmLabel: String, confirmEnabled: Boolean, onConfirm: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                TopAppBar(title = { Text(title, fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onDismiss) { Icon(Icons.Rounded.Close, "Close") } }, actions = { TextButton(onConfirm, enabled = confirmEnabled) { Text(confirmLabel) } })
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
            }
        }
    }
}

@Composable
private fun EditorSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    TallyCard(Modifier.fillMaxWidth()) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun ResetSelector(value: ResetReminder, onChange: (ResetReminder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ expanded = true }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.RestartAlt, null); Spacer(Modifier.width(8.dp)); Text("Reset: ${value.title}"); Spacer(Modifier.weight(1f)); Icon(Icons.Rounded.ExpandMore, null) }
        DropdownMenu(expanded, { expanded = false }) {
            ResetReminder.entries.forEach { item -> DropdownMenuItem(text = { Text(item.title) }, onClick = { onChange(item); expanded = false }) }
        }
    }
}

@Composable
private fun HexColorDialog(title: String, currentRaw: String, onUse: (String) -> Unit, onDismiss: () -> Unit) {
    var hex by remember { mutableStateOf(rawColorHex(currentRaw) ?: "0A84FF") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) { Surface(shape = MaterialTheme.shapes.extraLarge, color = colorFromRaw(customColorRaw(hex)), modifier = Modifier.fillMaxSize()) {} }
            OutlinedTextField(hex, { hex = it.uppercase().filter { ch -> ch in "0123456789ABCDEF" }.take(6) }, label = { Text("HEX") }, prefix = { Text("#") }, singleLine = true)
        }
    }, confirmButton = { Button({ onUse(customColorRaw(hex)) }, enabled = hex.length == 6) { Text("Use color") } }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}
