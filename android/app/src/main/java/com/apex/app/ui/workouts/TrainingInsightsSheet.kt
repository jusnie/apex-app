package com.apex.app.ui.workouts

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.apex.app.AppContainer
import com.apex.app.models.WorkoutInsights
import com.apex.app.models.WorkoutPersistedState
import com.apex.app.models.WorkoutWeightUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrainingInsightsSheet(
    visible: Boolean, initialCoach: Boolean, container: AppContainer,
    state: WorkoutDiaryUiState, persisted: WorkoutPersistedState, onDismiss: () -> Unit
) {
    if (!visible) return
    var coach by remember(initialCoach) { mutableStateOf(initialCoach) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF171E2B), contentColor = Color.White) {
        Column(Modifier.animateContentSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (coach) Button(onClick = { coach = true }) { Text("Coach") } else OutlinedButton(onClick = { coach = true }) { Text("Coach") }
                if (!coach) Button(onClick = { coach = false }) { Text("Consistency") } else OutlinedButton(onClick = { coach = false }) { Text("Consistency") }
            }
            if (coach) TrainingCoachContent(container, state, persisted) else ConsistencyContent(container, state, persisted)
        }
    }
}

@Composable
private fun TrainingCoachContent(container: AppContainer, state: WorkoutDiaryUiState, persisted: WorkoutPersistedState) {
    val scope = rememberCoroutineScope()
    val exercises = state.exercises.filterNot { it.isCardio }
    var chosenId by remember(state.selectedDate) { mutableStateOf<String?>(null) }
    val chosen = exercises.firstOrNull { it.itemId == chosenId } ?: exercises.firstOrNull()
    var brief by remember(chosen?.itemId) { mutableStateOf<String?>(null) }
    var error by remember(chosen?.itemId) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var mode by remember(chosen?.itemId) { mutableStateOf("base") }
    val suggestion = chosen?.let { WorkoutInsights.suggestion(persisted.dayPlans, it.itemId, state.selectedDate, state.weightUnit) }
    Text("TRAINING COACH", color = Color(0xFFFF968A), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    Text("Your next set, made clearer.", fontSize = 23.sp, fontWeight = FontWeight.Bold)
    Text("Suggestions use earlier logged workouts. Review the load and your technique before using them.", color = Color.LightGray, fontSize = 12.sp)
    if (exercises.isEmpty()) Text("Add a strength exercise to this day to view a suggestion.", fontSize = 13.sp)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        exercises.forEach { exercise -> OutlinedButton(onClick = { chosenId = exercise.itemId }) { Text(exercise.name, maxLines = 1) } }
    }
    if (chosen != null && suggestion == null) Text("No earlier weighted sets found for ${chosen.name}. Log a prior session to get started.", fontSize = 13.sp)
    if (chosen != null && suggestion != null) {
        val kg = when (mode) { "deload" -> suggestion.lastKg * 0.88; "reps" -> suggestion.lastKg; else -> suggestion.suggestedKg }
        val targetReps = suggestion.suggestedReps + if (mode == "reps") 1 else 0
        val display = if (state.weightUnit == WorkoutWeightUnit.LBS) kg * 2.2046226218 else kg
        val load = String.format(Locale.US, "%.1f", display).trimEnd('0').trimEnd('.')
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF4A2931), Color(0xFF252D3B))), RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(chosen.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("$load ${state.weightUnit.storageValue} × $targetReps", fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text(when (mode) { "deload" -> "Optional 12% reduction from your last top set; adjust to available plates."; "reps" -> "Optional one-rep increase at your previous load; maintain controlled technique."; else -> suggestion.rationale }, fontSize = 12.sp, color = Color.LightGray)
            Text("Based on ${suggestion.recentSessions} previous session(s).", fontSize = 11.sp, color = Color.LightGray)
            Text("Last 7 days: ${suggestion.primarySetsLast7Days} sets for primary muscles (workload, not measured recovery).", fontSize = 11.sp, color = Color.LightGray)
            suggestion.rpeDrift?.let { Text("RPE change across three sessions: ${String.format(Locale.getDefault(), "%+.1f", it)}", fontSize = 11.sp, color = Color.LightGray) }
            suggestion.estimatedOneRepMaxChangeKg?.let { Text("Estimated 1RM change across three sessions: ${String.format(Locale.getDefault(), "%+.1f", if (state.weightUnit == WorkoutWeightUnit.LBS) it * 2.2046226218 else it)} ${state.weightUnit.storageValue}", fontSize = 11.sp, color = Color.LightGray) }
            suggestion.estimatedOneRepMaxKg?.let { one ->
                val displayOne = if (state.weightUnit == WorkoutWeightUnit.LBS) one * 2.2046226218 else one
                Text("Estimated 1RM: ${String.format(Locale.getDefault(), "%.1f", displayOne)} ${state.weightUnit.storageValue}", fontSize = 11.sp, color = Color.LightGray)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { mode = "base" }) { Text("Base target") }
            OutlinedButton(onClick = { mode = "reps" }) { Text("Add rep") }
            OutlinedButton(onClick = { mode = "deload" }) { Text("Lighter set") }
        }
        Text("A formula estimate, not a recovery measurement or medical advice. Stop for pain and seek qualified help.", fontSize = 11.sp, color = Color.LightGray)
        Button(onClick = { confirm = true }, enabled = chosen.sets.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Use in first set") }
        if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Update first set?") }, text = { Text("This replaces the first set's weight and reps with $load ${state.weightUnit.storageValue} × $targetReps.") }, confirmButton = {
            TextButton(onClick = {
                confirm = false
                val set = chosen.sets.firstOrNull() ?: return@TextButton
                scope.launch { container.workoutRepository.updateSet(chosen.id, set.id, state.selectedDate, weight = load, weightUnit = state.weightUnit, reps = targetReps.toString()) }
            }) { Text("Update") }
        }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
        OutlinedButton(onClick = {
            scope.launch {
                busy = true; error = null
                try { brief = container.foodAnalysis.workoutInsightBrief("Exercise: ${suggestion.exerciseName}; previous top sets: ${suggestion.historyText}; strength RPE change: ${suggestion.rpeDrift ?: "unavailable"}; estimated 1RM change in kg: ${suggestion.estimatedOneRepMaxChangeKg ?: "unavailable"}; primary muscle workload over the preceding 7 days: ${suggestion.primarySetsLast7Days} sets; current proposed target: $kg kg × $targetReps reps. Workload is not measured recovery. No other data provided.") }
                catch (e: Exception) { if (e is CancellationException) throw e; error = e.localizedMessage ?: "Unable to get coaching." }
                finally { busy = false }
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { if (busy) CircularProgressIndicator() else Text("Ask Jarvis strategy") }
        Text("Asking Jarvis sends this exercise summary to your configured AI provider.", color = Color.LightGray, fontSize = 11.sp)
        brief?.let { Text(it, fontSize = 13.sp) }; error?.let { Text(it, color = Color(0xFFFFB4A9), fontSize = 12.sp) }
    }
}

@Composable
private fun ConsistencyContent(container: AppContainer, state: WorkoutDiaryUiState, persisted: WorkoutPersistedState) {
    val scope = rememberCoroutineScope(); val context = LocalContext.current
    var month by remember { mutableStateOf(YearMonth.from(state.selectedDate)) }
    var story by remember { mutableStateOf(false) }
    var graphite by remember { mutableStateOf(false) }
    var selectedDay by remember(month) { mutableStateOf<LocalDate?>(null) }
    var brief by remember(month) { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val summary = remember(month, persisted.dayPlans, state.weightUnit) { WorkoutInsights.month(persisted.dayPlans, month, state.weightUnit) }
    val layer = rememberGraphicsLayer()
    Text("TRAINING INSIGHTS", color = Color(0xFFFF968A), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    Text("Your consistency.", fontSize = 24.sp, fontWeight = FontWeight.Bold)
    Text("${summary.days.size} training days", fontSize = 24.sp, fontWeight = FontWeight.Bold)
    Text("${summary.performedSets} logged sets · ${String.format(Locale.getDefault(), "%,.0f", summary.volumeKg)} kg lifted · ${summary.trainingWeekStreak} training weeks", fontSize = 12.sp, color = Color.LightGray)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { month = month.minusMonths(1) }) { Text("Previous") }
        Text(month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + month.year, modifier = Modifier.padding(top = 12.dp), fontWeight = FontWeight.Bold)
        TextButton(onClick = { month = month.plusMonths(1) }) { Text("Next") }
    }
    Row(Modifier.fillMaxWidth()) { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), fontSize = 10.sp, color = Color.LightGray) } }
    val offset = month.atDay(1).dayOfWeek.value - 1; val count = month.lengthOfMonth(); val slots = ((offset + count + 6) / 7) * 7
    for (row in 0 until slots / 7) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (col in 0..6) {
                val day = row * 7 + col - offset + 1; val valid = day in 1..count
                val active = valid && month.atDay(day) in summary.days
                val daySets = if (valid) summary.daySetCounts[month.atDay(day)] ?: 0 else 0
                val color = when { !valid -> Color.Transparent; !active -> Color.White.copy(alpha = .07f); daySets >= 10 -> Color(0xFFF57868); daySets >= 5 -> Color(0xFFCC514B); else -> Color(0xFF873B41) }
                val selected = valid && month.atDay(day) == selectedDay
                Box(Modifier.weight(1f).aspectRatio(1f).background(color, RoundedCornerShape(8.dp))
                    .then(if (selected) Modifier.border(1.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
                    .then(if (valid) Modifier.clickable { selectedDay = month.atDay(day) } else Modifier).padding(7.dp)) {
                    if (valid) Text(day.toString(), fontSize = 11.sp)
                }
            }
        }
    }
    selectedDay?.let { day ->
        val sets = summary.daySetCounts[day] ?: 0
        Text("$day · ${if (day in summary.days) "$sets logged set(s)" else "No logged workout"}", fontSize = 12.sp, color = Color.LightGray)
    }
    Text("Shading reflects logged sets (darker: fewer, brighter: more). Timed-only workouts use the lowest shade. Training days include performed diary work; streak counts consecutive Monday-based weeks through the latest training week in this month.", color = Color.LightGray, fontSize = 11.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { story = false }) { Text("1:1 Square") }; OutlinedButton(onClick = { story = true }) { Text("9:16 Story") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { graphite = false }) { Text("Crimson") }; OutlinedButton(onClick = { graphite = true }) { Text("Graphite") }
    }
    Column(Modifier.fillMaxWidth().aspectRatio(if (story) 9f / 16f else 1f).drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }.background(Brush.linearGradient(listOf(if (graphite) Color(0xFF374355) else Color(0xFF512A34), Color(0xFF141B28))), RoundedCornerShape(12.dp)).padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Text("APEX FITNESS GYM  /  ${month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase()} ${month.year}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Column { Text("Built by\nconsistency.", fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp); Spacer(Modifier.height(6.dp)); Text("Every session counts.", fontSize = 11.sp) }
        Column {
            Text("${summary.days.size} DAYS    ${summary.performedSets} SETS    ${summary.trainingWeekStreak} WK", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("${String.format(Locale.getDefault(), "%,.0f", summary.volumeKg)} KG LIFTED", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
    Button(onClick = {
        scope.launch {
            try {
                val bitmap = layer.toImageBitmap().asAndroidBitmap()
                val uri = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "workout_share").apply { mkdirs() }; val file = File(dir, "apex_month_${System.currentTimeMillis()}.png")
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share recap"))
            } catch (e: Exception) { if (e is CancellationException) throw e; error = e.localizedMessage ?: "Unable to share the recap." }
        }
    }, modifier = Modifier.fillMaxWidth()) { Text("Share recap") }
    OutlinedButton(onClick = {
        scope.launch {
            busy = true; error = null
            try { brief = container.foodAnalysis.workoutInsightBrief("For $month: ${summary.days.size} training days, ${summary.performedSets} sets, ${summary.volumeKg.toInt()} kg total volume, ${summary.trainingWeekStreak} consecutive training weeks. No other data provided.") }
            catch (e: Exception) { if (e is CancellationException) throw e; error = e.localizedMessage ?: "Unable to get a summary." }
            finally { busy = false }
        }
    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Preparing…" else "Ask Jarvis for a monthly brief") }
    Text("Asking Jarvis sends these monthly totals to your configured AI provider.", color = Color.LightGray, fontSize = 11.sp)
    brief?.let { Text(it, fontSize = 13.sp) }; error?.let { Text(it, color = Color(0xFFFFB4A9), fontSize = 12.sp) }
}
