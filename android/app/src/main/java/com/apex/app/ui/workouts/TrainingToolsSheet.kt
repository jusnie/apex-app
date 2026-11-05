package com.apex.app.ui.workouts

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.app.AppContainer
import com.apex.app.data.ExerciseItem
import com.apex.app.models.TrainingTools
import com.apex.app.models.WorkoutPersistedState
import com.apex.app.models.WorkoutWeightUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrainingToolsSheet(visible: Boolean, container: AppContainer, state: WorkoutDiaryUiState, persisted: WorkoutPersistedState, library: List<ExerciseItem>, onDismiss: () -> Unit) {
    if (!visible) return
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("apex_training_tools", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val date = minOf(state.selectedDate, LocalDate.now())
    val week = remember(persisted.dayPlans, date, state.weightUnit) { TrainingTools.weekly(persisted.dayPlans, date, state.weightUnit) }
    val records = remember(persisted.dayPlans, date, state.weightUnit) { TrainingTools.records(persisted.dayPlans, date, state.weightUnit) }
    var tab by remember { mutableStateOf(0) }
    var note by remember(week.start) { mutableStateOf(prefs.getString("week.${week.start}", "") ?: "") }
    var energy by remember(date) { mutableStateOf(prefs.getInt("energy.$date", 0)) }
    var sleep by remember(date) { mutableStateOf(prefs.getInt("sleep.$date", 0)) }
    var soreness by remember(date) { mutableStateOf(prefs.getInt("soreness.$date", 0)) }
    var message by remember { mutableStateOf<String?>(null) }
    var exerciseId by remember(date) { mutableStateOf<UUID?>(null) }
    var candidate by remember { mutableStateOf<ExerciseItem?>(null) }
    val exercise = state.exercises.firstOrNull { it.id == exerciseId } ?: state.exercises.firstOrNull()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF171E2B), contentColor = Color.White) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("TRAINING TOOLS", color = Color(0xFFFF968A), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Weekly Check-in", "Readiness", "Records", "Exercise Swaps").forEachIndexed { index, title ->
                    if (tab == index) Button(onClick = { tab = index; message = null }) { Text(title) }
                    else OutlinedButton(onClick = { tab = index; message = null }) { Text(title) }
                }
            }
            when (tab) {
                0 -> {
                    Text("Your week.", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("${week.start} to ${week.end} · through $date", fontSize = 12.sp, color = Color.LightGray)
                    Text("${week.trainingDays} training days  ·  ${week.sets} logged sets", fontSize = 16.sp)
                    Text("${String.format(Locale.getDefault(), "%,.1f", week.volumeKg)} kg total volume", color = Color.LightGray, fontSize = 12.sp)
                    Text("Volume includes logged weighted strength sets; timed work can count as a training day. This is not a recovery score.", color = Color.LightGray, fontSize = 11.sp)
                    OutlinedTextField(value = note, onValueChange = { note = it.take(500); message = null }, label = { Text("Reflection or next goal") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Button(onClick = { prefs.edit().putString("week.${week.start}", note).apply(); message = "Check-in saved on this device." }) { Text("Save check-in") }
                }
                1 -> {
                    Text("How do you feel?", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("A quick self-check for $date. Choose what feels right; no automated diagnosis or load changes.", color = Color.LightGray, fontSize = 12.sp)
                    RatingRow("Energy", energy, listOf("Low", "Okay", "High")) { energy = it; message = null }
                    RatingRow("Sleep", sleep, listOf("Poor", "Okay", "Good")) { sleep = it; message = null }
                    RatingRow("Soreness", soreness, listOf("Low", "Moderate", "High")) { soreness = it; message = null }
                    Button(onClick = { prefs.edit().putInt("energy.$date", energy).putInt("sleep.$date", sleep).putInt("soreness.$date", soreness).apply(); message = "Readiness check saved on this device." }, enabled = energy > 0 && sleep > 0 && soreness > 0) { Text("Save readiness check") }
                    Text("Consider a lighter session or rest if you feel unwell. Stop for pain and seek qualified advice when needed.", color = Color.LightGray, fontSize = 11.sp)
                }
                2 -> {
                    Text("Your records.", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("New heaviest logged weights, through $date. Reps must be logged; these are not estimated one-rep maxes.", color = Color.LightGray, fontSize = 12.sp)
                    if (records.isEmpty()) Text("No weighted sets logged yet.")
                    records.take(100).forEach { record ->
                        val weight = if (state.weightUnit == WorkoutWeightUnit.LBS) record.weightKg * 2.2046226218 else record.weightKg
                        Text("${record.date}  ·  ${record.name}", fontWeight = FontWeight.Bold)
                        Text("${String.format(Locale.getDefault(), "%.1f", weight)} ${state.weightUnit.storageValue} × ${record.reps}", color = Color.LightGray, fontSize = 12.sp)
                    }
                    if (records.size > 100) Text("Showing the latest 100 milestones.", color = Color.LightGray, fontSize = 11.sp)
                }
                else -> {
                    Text("Find an alternative.", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("Suggestions share a primary muscle; they are not guaranteed equivalent or safe. Review equipment, technique and any limitations.", color = Color.LightGray, fontSize = 12.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.exercises.forEach { ex -> OutlinedButton(onClick = { exerciseId = ex.id; message = null }) { Text(ex.name) } }
                    }
                    if (exercise == null) Text("Add an exercise to this day first.") else {
                        Text("Alternatives for ${exercise.name}", fontWeight = FontWeight.Bold)
                        if (exercise.sets.any { it.hasLoggedValue } || exercise.timer?.hasStarted == true) Text("This entry has logged data and cannot be swapped. Add an alternative separately instead.", color = Color.LightGray, fontSize = 12.sp)
                        val matches = TrainingTools.alternatives(exercise, library, state.exercises.map { it.itemId }.toSet())
                        if (matches.isEmpty()) Text("No matching alternatives found.")
                        matches.forEach { item ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) { Text(item.name, fontSize = 13.sp); Text(item.equipment, fontSize = 11.sp, color = Color.LightGray) }
                                TextButton(onClick = { candidate = item }, enabled = exercise.sets.none { it.hasLoggedValue } && exercise.timer?.hasStarted != true) { Text("Swap") }
                            }
                        }
                    }
                }
            }
            message?.let { Text(it, color = Color.LightGray, fontSize = 12.sp) }
        }
    }
    val pending = candidate
    if (pending != null && exercise != null) AlertDialog(onDismissRequest = { candidate = null }, title = { Text("Swap exercise?") }, text = { Text("Replace the unlogged ${exercise.name} entry with ${pending.name}? Review its instructions and use an appropriate load.") }, confirmButton = {
        TextButton(onClick = { candidate = null; scope.launch {
            try { message = if (container.workoutRepository.swapUnloggedExercise(state.selectedDate.toString(), exercise.id, pending)) "Exercise swapped." else "The exercise changed or now contains logged data; no swap was made." }
            catch (e: Exception) { if (e is CancellationException) throw e; message = "Could not save the swap." }
        } }) { Text("Swap") }
    }, dismissButton = { TextButton(onClick = { candidate = null }) { Text("Cancel") } })
}

@Composable
private fun RatingRow(label: String, selected: Int, choices: List<String>, onSelect: (Int) -> Unit) {
    Text(label, fontWeight = FontWeight.Bold)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEachIndexed { i, choice ->
            if (selected == i + 1) Button(onClick = { onSelect(i + 1) }) { Text(choice) }
            else OutlinedButton(onClick = { onSelect(i + 1) }) { Text(choice) }
        }
    }
}
