@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.samua.tally.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.samua.tally.data.TallyStore
import com.samua.tally.model.TallyCounter
import com.samua.tally.model.TallySession
import com.samua.tally.model.formatAppleDate
import com.samua.tally.ui.StatCard
import com.samua.tally.ui.TallyCard
import com.samua.tally.ui.formatDuration
import kotlinx.coroutines.delay

@Composable
fun SessionsScreen(store: TallyStore, contentPadding: PaddingValues) {
    val state by store.state.collectAsState()
    var create by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(title = { Text("Sessions", fontWeight = FontWeight.Black) }, actions = { IconButton({ create = true }) { Icon(Icons.Rounded.AddCircle, "New Session") } })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Active", state.sessions.count { it.isActive }.toString(), Icons.Rounded.Timer, Modifier.weight(1f))
                    StatCard("Completed", state.sessions.count { !it.isActive }.toString(), Icons.Rounded.CheckCircle, Modifier.weight(1f))
                }
            }
            item {
                Button({ create = true }, Modifier.fillMaxWidth().height(54.dp)) { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Start Focus Session") }
            }
            if (state.sessions.none { it.isActive }) {
                item { Text("No active sessions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            } else {
                item { Text("Active", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(state.sessions.filter { it.isActive }, key = { it.id }) { session -> SessionCard(session, true, { store.endSession(session.id) }, { store.deleteSession(session.id) }) }
            }
            item { Text("Recent Sessions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            val completed = state.sessions.filterNot { it.isActive }
            if (completed.isEmpty()) item { Text("Completed sessions will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else items(completed, key = { it.id }) { session -> SessionCard(session, false, {}, { store.deleteSession(session.id) }) }
        }
    }
    if (create) SessionEditorDialog(store, onDismiss = { create = false })
}

@Composable
private fun SessionCard(session: TallySession, active: Boolean, onEnd: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val elapsed by produceState(initialValue = session.durationSeconds, session.id, session.endedAt) {
        while (session.isActive) { value = session.durationSeconds; delay(1000) }
    }
    TallyCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (active) Icons.Rounded.Timer else Icons.Rounded.CheckCircle, null, tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(session.title, fontWeight = FontWeight.ExtraBold)
                Text(session.counterName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatAppleDate(session.startedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatDuration(elapsed), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Box {
                IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, null) }
                DropdownMenu(menu, { menu = false }) {
                    if (active) DropdownMenuItem(text = { Text("End Session") }, leadingIcon = { Icon(Icons.Rounded.StopCircle, null) }, onClick = { onEnd(); menu = false })
                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { onDelete(); menu = false })
                }
            }
        }
        if (session.notes.isNotBlank()) Text(session.notes, style = MaterialTheme.typography.bodyMedium)
        session.delta?.let { Text("Change: ${if (it >= 0) "+" else ""}$it", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
        if (active) Button(onEnd, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Stop, null); Spacer(Modifier.width(8.dp)); Text("End Session") }
    }
}

@Composable
private fun SessionEditorDialog(store: TallyStore, onDismiss: () -> Unit) {
    val state by store.state.collectAsState()
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var counterId by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val selected = state.counters.firstOrNull { it.id == counterId && !it.isArchived }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Timer, null, modifier = Modifier.size(40.dp)) },
        title = { Text("New Session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Session name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Box {
                    OutlinedButton({ menu = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(if (selected == null) Icons.Rounded.Person else Icons.Rounded.Numbers, null)
                        Spacer(Modifier.width(8.dp)); Text(selected?.name ?: "Standalone"); Spacer(Modifier.weight(1f)); Icon(Icons.Rounded.ExpandMore, null)
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Standalone") }, leadingIcon = { Icon(Icons.Rounded.Person, null) }, onClick = { counterId = null; menu = false })
                        state.counters.filterNot { it.isArchived }.forEach { counter ->
                            DropdownMenuItem(text = { Text(counter.name) }, leadingIcon = { Icon(Icons.Rounded.Numbers, null) }, onClick = { counterId = counter.id; if (title.isBlank()) title = counter.name; menu = false })
                        }
                    }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Optional notes") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            }
        },
        confirmButton = { Button({ store.startSession(counterId, title, notes); onDismiss() }) { Text("Start") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}
