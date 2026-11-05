package com.apex.app.ui.workouts

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.animateContentSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.app.AppContainer
import com.apex.app.data.ExerciseRepository
import com.apex.app.models.Gender
import com.apex.app.models.WorkoutPersistedState
import com.apex.app.services.ai.MachineVisionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.min

/** Photo is sent only after the user taps Analyze. Results are advisory, never a guaranteed identification. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MachineGuideSheet(
    visible: Boolean,
    container: AppContainer,
    repository: ExerciseRepository,
    workoutState: WorkoutPersistedState,
    defaultGender: Gender,
    addedIds: Set<String>,
    onAdd: (com.apex.app.data.ExerciseItem) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var result by remember { mutableStateOf<MachineVisionResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var selectedSlugs by remember { mutableStateOf(emptySet<String>()) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) { image = bmp; result = null; error = null }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    var sample = 1
                    while (maxOf(opts.outWidth, opts.outHeight) / sample > 1600) sample *= 2
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    }
                }.getOrNull()
            }
            if (bitmap != null) { image = bitmap; result = null; error = null } else error = "Unable to open this image."
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF171E2B), contentColor = Color.White) {
        Column(Modifier.animateContentSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("EQUIPMENT INTELLIGENCE", color = Color(0xFFFF968A), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("Meet your machine.", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("Capture the full machine, including handles and seat.", color = Color.LightGray, fontSize = 13.sp)
            image?.let { photo ->
                Image(photo.asImageBitmap(), contentDescription = "Selected machine photo", modifier = Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Fit)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { camera.launch(null) }, enabled = !busy, modifier = Modifier.weight(1f)) { Icon(Icons.Default.CameraAlt, null, Modifier.size(17.dp)); Text("  Camera") }
                OutlinedButton(onClick = { gallery.launch("image/*") }, enabled = !busy, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PhotoLibrary, null, Modifier.size(17.dp)); Text("  Photos") }
            }
            Text("Analyzing sends the selected photo to your configured AI provider.", fontSize = 11.sp, color = Color.LightGray)
            Button(enabled = image != null && !busy, onClick = {
                val selected = image ?: return@Button
                scope.launch {
                    busy = true; error = null; result = null
                    try {
                        val bytes = withContext(Dispatchers.Default) {
                            val scale = min(1f, 1280f / maxOf(selected.width, selected.height))
                            val resized = if (scale < 1f) Bitmap.createScaledBitmap(selected, (selected.width * scale).toInt().coerceAtLeast(1), (selected.height * scale).toInt().coerceAtLeast(1), true) else selected
                            ByteArrayOutputStream().use { out -> resized.compress(Bitmap.CompressFormat.JPEG, 85, out); out.toByteArray() }
                        }
                        val found = container.foodAnalysis.analyzeMachine(bytes)
                        result = found
                        val names = found.primaryMuscles.map { it.lowercase() }.toSet()
                        selectedSlugs = APEX_MUSCLE_REGIONS.filterValues { m -> m.catalogMuscles.any { it.lowercase() in names } }.keys
                    } catch (e: Exception) { if (e is CancellationException) throw e; error = e.localizedMessage ?: "Unable to analyze this image." }
                    finally { busy = false }
                }
            }, modifier = Modifier.fillMaxWidth()) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Text("Analyze machine") }
            error?.let { Text(it, color = Color(0xFFFFB4A9), fontSize = 12.sp) }
            result?.let { found ->
                Column(Modifier.fillMaxWidth().background(Color(0xFF302B36), RoundedCornerShape(16.dp)).border(1.dp, Color(0x55F46B60), RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(if (found.uncertain) "IDENTIFICATION UNCERTAIN" else "IDENTIFICATION CANDIDATE", color = Color(0xFFFF9D91), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(found.name, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("Verify the label and ask a trainer if unsure.", fontSize = 12.sp, color = Color.LightGray)
                }
                if (!found.uncertain) {
                    if (found.primaryMuscles.isNotEmpty()) Text("Primary: ${found.primaryMuscles.joinToString()}", fontSize = 13.sp)
                    if (found.assistingMuscles.isNotEmpty()) Text("Assisting: ${found.assistingMuscles.joinToString()}", fontSize = 13.sp)
                    if (selectedSlugs.isNotEmpty()) {
                        BiometricBodyMapPanel(selectedSlugs, { selectedSlugs = it }, workoutState, defaultGender, 0)
                        Text("The map illustrates broad muscle groups; assisting highlights are estimates.", fontSize = 11.sp, color = Color.LightGray)
                    }
                    if (found.setupCues.isNotEmpty()) {
                        Text("Setup and form", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        found.setupCues.forEachIndexed { i, cue -> Text("${i + 1}. $cue", fontSize = 13.sp, color = Color.LightGray) }
                        Text("Start light; check the pin or plates. Stop if you feel pain.", fontSize = 11.sp, color = Color.LightGray)
                    }
                    val matches = remember(found.searchQuery, repository) {
                        if (found.searchQuery.isBlank()) emptyList() else repository.filtered(searchText = found.searchQuery).take(5)
                    }
                    if (matches.isNotEmpty()) {
                        Text("Possible library matches", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        matches.forEach { exercise ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(exercise.name, Modifier.weight(1f), fontSize = 12.sp)
                                if (exercise.id in addedIds) Text("Added", color = Color.LightGray, fontSize = 12.sp)
                                else OutlinedButton(onClick = { onAdd(exercise) }) { Text("Add") }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
