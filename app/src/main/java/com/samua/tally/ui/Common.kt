package com.samua.tally.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.samua.tally.model.presetColors
import com.samua.tally.model.symbolOptions
import com.samua.tally.ui.theme.colorFromRaw

fun iconForSymbol(symbol: String): ImageVector = when (symbol) {
    "checkmark.seal.fill" -> Icons.Rounded.Verified
    "drop.fill" -> Icons.Rounded.WaterDrop
    "flame.fill" -> Icons.Rounded.LocalFireDepartment
    "bolt.fill" -> Icons.Rounded.Bolt
    "book.fill" -> Icons.Rounded.MenuBook
    "cart.fill" -> Icons.Rounded.ShoppingCart
    "gamecontroller.fill" -> Icons.Rounded.SportsEsports
    "figure.strengthtraining.traditional" -> Icons.Rounded.FitnessCenter
    "star.fill" -> Icons.Rounded.Star
    "shippingbox.fill" -> Icons.Rounded.Inventory2
    "calendar" -> Icons.Rounded.CalendarMonth
    "trophy.fill" -> Icons.Rounded.EmojiEvents
    "timer" -> Icons.Rounded.Timer
    "list.bullet.clipboard.fill" -> Icons.Rounded.Checklist
    "dollarsign.circle.fill" -> Icons.Rounded.AttachMoney
    "pills.fill" -> Icons.Rounded.Medication
    "cup.and.saucer.fill" -> Icons.Rounded.LocalCafe
    "figure.walk" -> Icons.Rounded.DirectionsWalk
    "graduationcap.fill" -> Icons.Rounded.School
    else -> Icons.Rounded.Numbers
}

@Composable
fun TallyCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
}

@Composable
fun StatCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    TallyCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        }
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun ColorGrid(selectedRaw: String, onSelected: (String) -> Unit, allowCustom: Boolean = true, onCustom: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        presetColors.keys.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { raw ->
                    val color = colorFromRaw(raw)
                    Surface(
                        modifier = Modifier.weight(1f).height(54.dp).clickable { onSelected(raw) },
                        shape = RoundedCornerShape(16.dp),
                        color = color.copy(alpha = if (selectedRaw == raw) 0.28f else 0.13f),
                        border = if (selectedRaw == raw) androidx.compose.foundation.BorderStroke(2.dp, color) else null
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Box(Modifier.size(18.dp).clip(RoundedCornerShape(9.dp)).background(color))
                            Text(raw.replaceFirstChar { it.uppercase() }, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (allowCustom && onCustom != null) {
            OutlinedButton(onClick = onCustom, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Palette, null)
                Spacer(Modifier.width(8.dp))
                Text("Custom color")
            }
        }
    }
}

@Composable
fun SymbolGrid(selected: String, onSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        symbolOptions.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (symbol, title) ->
                    val selectedHere = selected == symbol
                    Surface(
                        modifier = Modifier.weight(1f).height(64.dp).clickable { onSelected(symbol) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selectedHere) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(iconForSymbol(symbol), title, modifier = Modifier.size(23.dp), tint = if (selectedHere) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%02d:%02d".format(minutes, secs)
}
