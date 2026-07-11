@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.samua.tally.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.samua.tally.data.TallyStore
import com.samua.tally.model.*
import com.samua.tally.ui.*
import com.samua.tally.ui.theme.colorFromRaw
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private class DragDropController {
    var draggedCounterId by mutableStateOf<String?>(null)
    var position by mutableStateOf(Offset.Zero)
    var rootOrigin by mutableStateOf(Offset.Zero)
    val targets: SnapshotStateMap<String, Rect> = mutableStateMapOf()

    fun start(id: String, point: Offset) { draggedCounterId = id; position = point }
    fun drag(delta: Offset) { position += delta }
    fun cancel() { draggedCounterId = null }
    fun finish(onDrop: (String, String?) -> Unit) {
        val id = draggedCounterId ?: return
        val target = targets.entries.lastOrNull { it.value.contains(position) }?.key
        when {
            target == "__unfiled__" -> onDrop(id, null)
            target != null -> onDrop(id, target)
        }
        draggedCounterId = null
    }
}

@Composable
fun CountersScreen(store: TallyStore, contentPadding: PaddingValues, onOpenCounter: (String) -> Unit) {
    val state by store.state.collectAsState()
    var search by remember { mutableStateOf("") }
    var createMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf("manual") }
    var editingCounter by remember { mutableStateOf<TallyCounter?>(null) }
    var editingFolder by remember { mutableStateOf<TallyFolder?>(null) }
    var addingCounter by remember { mutableStateOf(false) }
    var addingFolder by remember { mutableStateOf(false) }
    var quickFolder by remember { mutableStateOf<TallyFolder?>(null) }
    var exactCounter by remember { mutableStateOf<TallyCounter?>(null) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val drag = remember { DragDropController() }

    val active = state.counters.filterNot { it.isArchived }.filter {
        search.isBlank() || it.name.contains(search, true) || it.group.contains(search, true) || it.notes.contains(search, true)
    }.let { list ->
        when (sort) {
            "recent" -> list.sortedByDescending { it.updatedAt }
            "name" -> list.sortedBy { it.name.lowercase() }
            "value" -> list.sortedByDescending { it.value }
            else -> list
        }
    }
    val folderNames = state.folders.map { it.name.lowercase() }.toSet()
    val unfiled = active.filter { it.group.isBlank() || it.group.lowercase() !in folderNames }
    val pinned = active.filter { it.isPinned }
    val visibleFolders = state.folders.filter { folder -> search.isBlank() || folder.name.contains(search, true) || active.any { it.group.equals(folder.name, true) } }

    Box(
        Modifier.fillMaxSize().padding(contentPadding).onGloballyPositioned { drag.rootOrigin = it.boundsInRoot().topLeft }
    ) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Tally", fontWeight = FontWeight.Black) },
                navigationIcon = { IconButton(onClick = { store.undoLastAction() }, enabled = state.history.isNotEmpty()) { Icon(Icons.Rounded.Undo, "Undo") } },
                actions = {
                    Box {
                        IconButton({ sortMenu = true }) { Icon(Icons.Rounded.Sort, "Sort") }
                        DropdownMenu(sortMenu, { sortMenu = false }) {
                            listOf("manual" to "Manual", "recent" to "Recently updated", "name" to "Name", "value" to "Value").forEach { (key, title) ->
                                DropdownMenuItem(text = { Text(title) }, leadingIcon = { if (sort == key) Icon(Icons.Rounded.Check, null) }, onClick = { sort = key; sortMenu = false })
                            }
                        }
                    }
                    Box {
                        IconButton({ createMenu = true }) { Icon(Icons.Rounded.AddCircle, "Create") }
                        DropdownMenu(createMenu, { createMenu = false }) {
                            DropdownMenuItem(text = { Text("New Counter") }, leadingIcon = { Icon(Icons.Rounded.Numbers, null) }, onClick = { addingCounter = true; createMenu = false })
                            DropdownMenuItem(text = { Text("New Folder") }, leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null) }, onClick = { addingFolder = true; createMenu = false })
                        }
                    }
                }
            )
            OutlinedTextField(
                value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text("Search counters and folders") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true,
                trailingIcon = { if (search.isNotEmpty()) IconButton({ search = "" }) { Icon(Icons.Rounded.Close, null) } }
            )
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("Active", state.counters.count { !it.isArchived }.toString(), Icons.Rounded.Numbers, Modifier.weight(1f))
                        StatCard("Folders", state.folders.size.toString(), Icons.Rounded.Folder, Modifier.weight(1f))
                        StatCard("Total", active.sumOf { it.value }.toString(), Icons.Rounded.Calculate, Modifier.weight(1f))
                    }
                }
                if (pinned.isNotEmpty()) {
                    item { SectionTitle("Favorites") }
                    items(pinned, key = { "pin-${it.id}" }) { counter ->
                        CounterCardAndroid(counter, store, drag, onOpenCounter, { editingCounter = it }, { exactCounter = it })
                    }
                }
                items(visibleFolders, key = { it.id }) { folder ->
                    val counters = active.filter { it.group.equals(folder.name, true) }
                    FolderSection(
                        folder = folder,
                        counters = counters,
                        expanded = expanded[folder.id] ?: true,
                        onToggle = { expanded[folder.id] = !(expanded[folder.id] ?: true) },
                        onQuick = { quickFolder = folder },
                        onEdit = { editingFolder = folder },
                        onDelete = { store.deleteFolder(folder, true) },
                        drag = drag,
                        store = store,
                        onOpenCounter = onOpenCounter,
                        onEditCounter = { editingCounter = it },
                        onExact = { exactCounter = it }
                    )
                }
                item {
                    UnfiledSection(unfiled, drag, store, onOpenCounter, { editingCounter = it }, { exactCounter = it })
                }
                if (active.isEmpty() && state.folders.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.SpaceDashboard, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("No counters or folders", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Create a folder or counter to begin.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        val dragged = drag.draggedCounterId?.let { id -> state.counters.firstOrNull { it.id == id } }
        if (dragged != null) {
            Surface(
                modifier = Modifier.offset {
                    IntOffset((drag.position.x - drag.rootOrigin.x - 90).roundToInt(), (drag.position.y - drag.rootOrigin.y - 32).roundToInt())
                }.width(180.dp),
                shape = RoundedCornerShape(18.dp), shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surface
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(iconForSymbol(dragged.symbol), null, tint = colorFromRaw(dragged.colorName)); Spacer(Modifier.width(8.dp)); Text(dragged.name, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }

    if (addingCounter) CounterEditorDialog(store, onDismiss = { addingCounter = false })
    if (addingFolder) FolderEditorDialog(store, onDismiss = { addingFolder = false })
    editingCounter?.let { CounterEditorDialog(store, it, onDismiss = { editingCounter = null }) }
    editingFolder?.let { FolderEditorDialog(store, it, onDismiss = { editingFolder = null }) }
    quickFolder?.let { QuickCreateDialog(store, it, onDismiss = { quickFolder = null }) }
    exactCounter?.let { ExactValueDialog(store, it, onDismiss = { exactCounter = null }) }
}

@Composable
private fun FolderSection(
    folder: TallyFolder,
    counters: List<TallyCounter>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onQuick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    drag: DragDropController,
    store: TallyStore,
    onOpenCounter: (String) -> Unit,
    onEditCounter: (TallyCounter) -> Unit,
    onExact: (TallyCounter) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val tint = colorFromRaw(folder.colorRaw)
    Column(
        Modifier.fillMaxWidth().onGloballyPositioned { drag.targets[folder.id] = it.boundsInRoot() }
            .background(tint.copy(alpha = 0.06f), RoundedCornerShape(24.dp)).border(1.dp, tint.copy(alpha = 0.16f), RoundedCornerShape(24.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onToggle) { Icon(if (expanded) Icons.Rounded.FolderOpen else Icons.Rounded.Folder, null, tint = tint) }
            Column(Modifier.weight(1f)) {
                Text(folder.name, color = tint, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                Text("${counters.size} counters • Total ${counters.sumOf { it.value }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onQuick) { Icon(Icons.Rounded.AddCircle, "Quick Create", tint = tint) }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Folder menu") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit Folder") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { onEdit(); menu = false })
                    DropdownMenuItem(text = { Text("Quick Create") }, leadingIcon = { Icon(Icons.Rounded.AddCircle, null) }, onClick = { onQuick(); menu = false })
                    DropdownMenuItem(text = { Text("Delete Folder", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { onDelete(); menu = false })
                }
            }
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
        }
        if (expanded) {
            counters.filterNot { it.isPinned }.forEach { counter ->
                CounterCardAndroid(counter, store, drag, onOpenCounter, onEditCounter, onExact)
            }
            if (counters.none { !it.isPinned }) {
                Box(Modifier.fillMaxWidth().height(64.dp).background(tint.copy(alpha = 0.07f), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                    Text("Drop a counter here or use Quick Create", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun UnfiledSection(
    counters: List<TallyCounter>, drag: DragDropController, store: TallyStore,
    onOpenCounter: (String) -> Unit, onEdit: (TallyCounter) -> Unit, onExact: (TallyCounter) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().onGloballyPositioned { drag.targets["__unfiled__"] = it.boundsInRoot() }
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(24.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.MoveToInbox, null, tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(8.dp))
            Text("Unfiled", fontWeight = FontWeight.ExtraBold); Spacer(Modifier.weight(1f)); Text("Drop here to remove from a folder", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        counters.filterNot { it.isPinned }.forEach { counter -> CounterCardAndroid(counter, store, drag, onOpenCounter, onEdit, onExact) }
        if (counters.none { !it.isPinned }) {
            Box(Modifier.fillMaxWidth().height(58.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Text("Drop counters here", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun CounterCardAndroid(
    counter: TallyCounter,
    store: TallyStore,
    drag: DragDropController,
    onOpen: (String) -> Unit,
    onEdit: (TallyCounter) -> Unit,
    onExact: (TallyCounter) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val tint = colorFromRaw(counter.colorName)
    Card(
        Modifier.fillMaxWidth().onGloballyPositioned { bounds = it.boundsInRoot() }.pointerInput(counter.id) {
            detectDragGesturesAfterLongPress(
                onDragStart = { local -> drag.start(counter.id, bounds.topLeft + local) },
                onDrag = { change, amount -> change.consume(); drag.drag(amount) },
                onDragEnd = { drag.finish { id, folderId -> store.moveCounter(id, folderId) } },
                onDragCancel = { drag.cancel() }
            )
        },
        shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(onClick = { onOpen(counter.id) }, shape = RoundedCornerShape(14.dp), color = tint.copy(alpha = 0.14f)) {
                    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) { Icon(iconForSymbol(counter.symbol), null, tint = tint) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(counter.name, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (counter.isPinned) Icon(Icons.Rounded.PushPin, null, modifier = Modifier.size(16.dp))
                        if (counter.isLocked) Icon(Icons.Rounded.Lock, null, modifier = Modifier.size(16.dp))
                    }
                    when {
                        counter.goal != null -> Text("Goal: ${counter.value} / ${counter.goal}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        counter.notes.isNotBlank() -> Text(counter.notes, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (counter.resetReminder != ResetReminder.NONE) Text("${if (counter.automaticResetEnabled) "Auto " else ""}${counter.resetReminder.title}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton({ onExact(counter) }, enabled = !counter.isLocked) { Text(counter.value.toString(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = tint) }
            }
            counter.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth(), color = tint) }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton({ store.adjust(counter.id, -1) }, enabled = !counter.isLocked, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.weight(1f)) { Text("−1") }
                counter.stepValues.forEach { step ->
                    Button({ store.adjust(counter.id, step) }, enabled = !counter.isLocked, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.weight(1f)) { Text("+$step") }
                }
                Box {
                    FilledTonalIconButton({ menu = true }) { Icon(Icons.Rounded.MoreHoriz, "Actions") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(if (counter.isPinned) "Unpin" else "Pin") }, leadingIcon = { Icon(Icons.Rounded.PushPin, null) }, onClick = { store.togglePinned(counter); menu = false })
                        DropdownMenuItem(text = { Text(if (counter.isLocked) "Unlock" else "Lock") }, leadingIcon = { Icon(if (counter.isLocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock, null) }, onClick = { store.toggleLocked(counter); menu = false })
                        DropdownMenuItem(text = { Text("Exact Value") }, leadingIcon = { Icon(Icons.Rounded.Numbers, null) }, onClick = { onExact(counter); menu = false }, enabled = !counter.isLocked)
                        DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { onEdit(counter); menu = false })
                        DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { store.duplicateCounter(counter); menu = false })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Reset") }, leadingIcon = { Icon(Icons.Rounded.RestartAlt, null) }, onClick = { confirmReset = true; menu = false }, enabled = !counter.isLocked)
                        DropdownMenuItem(text = { Text("Archive") }, leadingIcon = { Icon(Icons.Rounded.Archive, null) }, onClick = { confirmArchive = true; menu = false })
                        DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) }, onClick = { confirmDelete = true; menu = false })
                    }
                }
            }
        }
    }
    if (confirmReset) ConfirmDialog("Reset ${counter.name}?", "The current value will become zero.", "Reset", { store.reset(counter.id); confirmReset = false }, { confirmReset = false })
    if (confirmArchive) ConfirmDialog("Archive ${counter.name}?", "You can restore it later from Settings.", "Archive", { store.archiveCounter(counter); confirmArchive = false }, { confirmArchive = false })
    if (confirmDelete) ConfirmDialog("Delete ${counter.name} permanently?", "This removes the counter, its history, and linked sessions.", "Delete forever", { store.permanentlyDeleteCounter(counter); confirmDelete = false }, { confirmDelete = false })
}

@Composable
private fun ConfirmDialog(title: String, message: String, action: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) }, confirmButton = { Button(onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(action) } }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
fun CounterDetailScreen(store: TallyStore, counterId: String, contentPadding: PaddingValues, onBack: () -> Unit) {
    val state by store.state.collectAsState()
    val counter = state.counters.firstOrNull { it.id == counterId }
    if (counter == null) {
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) { Text("Counter not found") }
        return
    }
    val tint = colorFromRaw(counter.colorName)
    var edit by remember { mutableStateOf(false) }
    var exact by remember { mutableStateOf(false) }
    val history = state.history.filter { it.counterID == counter.id }.take(30)
    val sessions = state.sessions.filter { it.counterID == counter.id }.take(10)
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(title = { Text(counter.name, fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onBack) { Icon(Icons.Rounded.ArrowBack, "Back") } }, actions = { IconButton({ edit = true }) { Icon(Icons.Rounded.Edit, "Edit") } })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                TallyCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(iconForSymbol(counter.symbol), null, tint = tint, modifier = Modifier.size(42.dp)); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(counter.displayGroup, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(counter.value.toString(), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black, color = tint) }
                        OutlinedButton({ exact = true }, enabled = !counter.isLocked) { Text("Set value") }
                    }
                    counter.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth(), color = tint) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ store.adjust(counter.id, -1) }, enabled = !counter.isLocked, modifier = Modifier.weight(1f)) { Text("−1") }
                        counter.stepValues.forEach { step -> Button({ store.adjust(counter.id, step) }, enabled = !counter.isLocked, modifier = Modifier.weight(1f)) { Text("+$step") } }
                    }
                }
            }
            item { SectionTitle("Milestones") }
            item {
                TallyCard(Modifier.fillMaxWidth()) {
                    if (counter.milestones.isEmpty()) Text("No milestones configured")
                    else counter.milestones.forEach { milestone ->
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(if (milestone in counter.reachedMilestones) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (milestone in counter.reachedMilestones) tint else MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(8.dp)); Text(milestone.toString()) }
                    }
                }
            }
            if (counter.notes.isNotBlank()) { item { SectionTitle("Notes") }; item { TallyCard(Modifier.fillMaxWidth()) { Text(counter.notes) } } }
            item { SectionTitle("Recent history") }
            if (history.isEmpty()) item { Text("No history yet", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else items(history, key = { it.id }) { entry ->
                ListItem(headlineContent = { Text(entry.action, fontWeight = FontWeight.SemiBold) }, supportingContent = { Text(formatAppleDate(entry.date)) }, trailingContent = { Text(if (entry.delta > 0) "+${entry.delta}" else entry.delta.toString(), color = tint) })
            }
            item { SectionTitle("Sessions") }
            if (sessions.isEmpty()) item { Text("No linked sessions", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else items(sessions, key = { it.id }) { session -> ListItem(headlineContent = { Text(session.title) }, supportingContent = { Text(formatAppleDate(session.startedAt)) }, trailingContent = { Text(formatDuration(session.durationSeconds)) }) }
        }
    }
    if (edit) CounterEditorDialog(store, counter, onDismiss = { edit = false })
    if (exact) ExactValueDialog(store, counter, onDismiss = { exact = false })
}
