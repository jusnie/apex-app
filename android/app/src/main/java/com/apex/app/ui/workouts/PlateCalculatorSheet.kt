package com.apex.app.ui.workouts

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.app.models.PlateInventory
import com.apex.app.models.PlateLoadingCalculator
import com.apex.app.models.PlateLoadingResult
import com.apex.app.models.WorkoutWeightUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private data class PlateDraft(val id: Int, val weight: String, val count: String)
private data class PlateSettings(val bar: String, val rows: List<PlateDraft>)

private fun plateDefaults(unit: WorkoutWeightUnit): PlateSettings {
    val values = if (unit == WorkoutWeightUnit.KG) listOf("25", "20", "15", "10", "5", "2.5", "1.25")
        else listOf("45", "35", "25", "10", "5", "2.5", "1.25")
    val dominant = if (unit == WorkoutWeightUnit.KG) "20" else "45"
    return PlateSettings(if (unit == WorkoutWeightUnit.KG) "20" else "45", values.mapIndexed { i, w -> PlateDraft(i, w, if (w == dominant) "4" else "2") })
}

private fun readPlateSettings(context: Context, unit: WorkoutWeightUnit): PlateSettings {
    val stored = context.getSharedPreferences("apex_plate_calculator", Context.MODE_PRIVATE).getString(unit.storageValue, null) ?: return plateDefaults(unit)
    return runCatching {
        val json = JSONObject(stored)
        val arr = json.getJSONArray("plates")
        require(arr.length() <= PlateLoadingCalculator.MAX_ROWS)
        val rows = (0 until arr.length()).map { i ->
            val item = arr.getJSONObject(i)
            PlateDraft(i, item.getString("weight").take(10), item.getString("count").take(2))
        }
        PlateSettings(json.getString("bar").take(10), rows)
    }.getOrElse { plateDefaults(unit) }
}

private fun savePlateSettings(context: Context, unit: WorkoutWeightUnit, bar: String, rows: List<PlateDraft>) {
    val arr = JSONArray()
    rows.forEach { arr.put(JSONObject().put("weight", it.weight).put("count", it.count)) }
    context.getSharedPreferences("apex_plate_calculator", Context.MODE_PRIVATE).edit()
        .putString(unit.storageValue, JSONObject().put("bar", bar).put("plates", arr).toString()).apply()
}

private fun formatPlateWeight(value: Int): String {
    val whole = value / 1000
    val decimal = (value % 1000).toString().padStart(3, '0').trimEnd('0')
    return if (decimal.isEmpty()) whole.toString() else "$whole.$decimal"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlateCalculatorSheet(visible: Boolean, defaultUnit: WorkoutWeightUnit, onDismiss: () -> Unit) {
    if (!visible) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var unit by remember { mutableStateOf(defaultUnit) }
    val initial = remember { readPlateSettings(context, defaultUnit) }
    var bar by remember { mutableStateOf(initial.bar) }
    var rows by remember { mutableStateOf(initial.rows) }
    var target by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<PlateLoadingResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun updateUnit(next: WorkoutWeightUnit) {
        if (next == unit) return
        unit = next
        val saved = readPlateSettings(context, next)
        bar = saved.bar; rows = saved.rows; target = ""; result = null; error = null
    }
    fun invalidate() { result = null; error = null }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, containerColor = Color(0xFF171E2B), contentColor = Color.White) {
        Column(Modifier.animateContentSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("TRAINING TOOL / OFFLINE", color = Color(0xFFFF968A), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("Load your bar.", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("Find a balanced plate setup using the equipment you have.", color = Color.LightGray, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (unit == WorkoutWeightUnit.KG) Button(onClick = { updateUnit(WorkoutWeightUnit.KG) }, enabled = !busy) { Text("kg") }
                else OutlinedButton(onClick = { updateUnit(WorkoutWeightUnit.KG) }, enabled = !busy) { Text("kg") }
                if (unit == WorkoutWeightUnit.LBS) Button(onClick = { updateUnit(WorkoutWeightUnit.LBS) }, enabled = !busy) { Text("lb") }
                else OutlinedButton(onClick = { updateUnit(WorkoutWeightUnit.LBS) }, enabled = !busy) { Text("lb") }
            }
            Text("Switching units loads separate saved bar and plate settings.", fontSize = 11.sp, color = Color.LightGray)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = target, onValueChange = { target = it.take(10); invalidate() }, label = { Text("Target total") }, suffix = { Text(unit.storageValue) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = !busy, modifier = Modifier.weight(1f))
                OutlinedTextField(value = bar, onValueChange = { bar = it.take(10); invalidate() }, label = { Text("Bar weight") }, suffix = { Text(unit.storageValue) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = !busy, modifier = Modifier.weight(1f))
            }
            Text("Target includes the bar. Verify its actual weight; collars are not included.", fontSize = 11.sp, color = Color.LightGray)
            Text("Example equipment is prefilled. Edit it to match your gym before calculating.", fontSize = 11.sp, color = Color.LightGray)
            Text("Your plates", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Enter the total number available, not the number per side. Unpaired plates are left unused.", fontSize = 11.sp, color = Color.LightGray)
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = row.weight, onValueChange = { value -> rows = rows.map { if (it.id == row.id) it.copy(weight = value.take(10)) else it }; invalidate() }, label = { Text("Plate ${unit.storageValue}") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = !busy, modifier = Modifier.weight(1.1f))
                    OutlinedTextField(value = row.count, onValueChange = { value -> rows = rows.map { if (it.id == row.id) it.copy(count = value.take(2)) else it }; invalidate() }, label = { Text("Total plates") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, enabled = !busy, modifier = Modifier.weight(1f))
                    TextButton(onClick = { rows = rows.filterNot { it.id == row.id }; invalidate() }, enabled = !busy) { Text("Remove") }
                }
            }
            if (rows.size < PlateLoadingCalculator.MAX_ROWS) TextButton(onClick = {
                rows = rows + PlateDraft((rows.maxOfOrNull { it.id } ?: -1) + 1, "", "2"); invalidate()
            }, enabled = !busy) { Text("Add plate size") }
            Button(onClick = {
                val desired = PlateLoadingCalculator.parseWeight(target)
                val barWeight = PlateLoadingCalculator.parseWeight(bar)
                val parsed = rows.map { row ->
                    val w = PlateLoadingCalculator.parseWeight(row.weight)
                    val c = row.count.toIntOrNull()
                    if (w == null || w <= 0 || c == null || c !in 0..PlateLoadingCalculator.MAX_COUNT) null else PlateInventory(w, c)
                }
                when {
                    desired == null || desired <= 0 -> error = "Enter a target from 0.001 to 1,000 ${unit.storageValue}."
                    barWeight == null -> error = "Enter a valid bar weight up to 1,000 ${unit.storageValue}."
                    desired < barWeight -> error = "The target cannot be less than the bar weight."
                    parsed.any { it == null } -> error = "Enter a positive plate weight and a total count from 0 to 20 for each row."
                    else -> {
                        val inventory = parsed.filterNotNull()
                        savePlateSettings(context, unit, bar, rows)
                        scope.launch {
                            busy = true; error = null; result = null
                            try { result = withContext(Dispatchers.Default) { PlateLoadingCalculator.calculate(desired, barWeight, inventory) } }
                            finally { busy = false }
                        }
                    }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { if (busy) CircularProgressIndicator(color = Color.White) else Text("Calculate plates") }
            error?.let { Text(it, color = Color(0xFFFFB4A9), fontSize = 12.sp) }
            result?.let { value ->
                Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF4A2931), Color(0xFF252D3B))), RoundedCornerShape(16.dp)).border(1.dp, Color(0x55F46B60), RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (value.isExact) "TARGET MATCHED" else "CLOSEST AT OR BELOW TARGET", color = Color(0xFFFFA198), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("${formatPlateWeight(value.actualMilli)} ${unit.storageValue}", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    if (!value.isExact) Text("${formatPlateWeight(value.shortfallMilli)} ${unit.storageValue} below target", fontSize = 12.sp, color = Color.LightGray)
                    Text("Each side", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (value.perSide.isEmpty()) Text(if (value.isExact) "No plates needed." else "No matching pairs can be loaded without exceeding the target.", fontSize = 12.sp)
                    value.perSide.forEach { plate -> Text("${plate.count} × ${formatPlateWeight(plate.weightMilli)} ${unit.storageValue}", fontSize = 14.sp) }
                    Text("Bar: ${formatPlateWeight(value.barMilli)} ${unit.storageValue} · same plates on both sides", fontSize = 11.sp, color = Color.LightGray)
                }
                Text("Double-check the bar, plates, and collars before lifting. This setup excludes collar weight.", fontSize = 11.sp, color = Color.LightGray)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
