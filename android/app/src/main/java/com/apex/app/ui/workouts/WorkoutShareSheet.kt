package com.apex.app.ui.workouts

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.apex.app.R
import com.apex.app.models.PlannedExercise
import com.apex.app.models.WorkoutDate
import com.apex.app.models.WorkoutPersistedState
import com.apex.app.models.WorkoutWeightUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

enum class ShareCardFormat(val title: String, val subtitle: String) {
    STORY("9:16 Story Card", "Full-screen IG & FB Story"),
    HUD("Transparent Photo HUD", "Frosted overlay on gym photo"),
    SQUARE("1:1 Feed Post Card", "Square 1080x1080 summary"),
    PR("PR Record Card", "Personal Record spotlight")
}

enum class ShareCardTheme(val title: String, val subtitle: String) {
    TACTICAL("Tactical Aurora", "APEX Frosted Dark"),
    CRIMSON("Crimson Surge", "Bold Red Metallic"),
    STEALTH("Stealth Matte", "Clean Minimal Slate")
}

internal data class ShareLiftItem(
    val name: String,
    val setCount: Int,
    val bestKg: Double,
    val bestReps: Int,
    val isPr: Boolean,
    val est1RmKg: Double
)

internal data class WorkoutShareSummary(
    val sessionTitle: String,
    val dateLabel: String,
    val durationLabel: String,
    val totalSets: Int,
    val totalVolumeKg: Double,
    val caloriesKcal: Int,
    val lifts: List<ShareLiftItem>,
    val prCount: Int,
    val topPrLift: ShareLiftItem?,
    val muscles: List<String>
)

internal fun buildWorkoutShareSummary(
    selectedDate: LocalDate,
    exercises: List<PlannedExercise>,
    caloriesBurned: Int?,
    persistedState: WorkoutPersistedState
): WorkoutShareSummary {
    val selectedDateKey = WorkoutDate.key(selectedDate)

    // Historical max estimated 1RM per exercise before selectedDate for real PR detection
    val historicalMax1RmKg = mutableMapOf<String, Double>()
    for ((dateKey, plan) in persistedState.dayPlans) {
        if (dateKey >= selectedDateKey) continue
        for (ex in plan.exercises) {
            for (s in ex.sets) {
                val reps = s.reps.toIntOrNull() ?: continue
                if (reps <= 0) continue
                val wRaw = s.weight.replace(',', '.').toDoubleOrNull() ?: 0.0
                val wKg = if ((s.weightUnit ?: WorkoutWeightUnit.KG) == WorkoutWeightUnit.LBS) wRaw / 2.2046226218 else wRaw
                val e1rm = wKg * (1.0 + reps / 30.0)
                val prev = historicalMax1RmKg[ex.itemId] ?: 0.0
                if (e1rm > prev) historicalMax1RmKg[ex.itemId] = e1rm
            }
        }
    }

    var totalSets = 0
    var totalVolumeKg = 0.0
    var timerSeconds = 0.0
    val lifts = mutableListOf<ShareLiftItem>()
    val muscleSet = linkedSetOf<String>()

    for (ex in exercises) {
        ex.primaryMuscles.forEach { m ->
            if (m.isNotBlank()) muscleSet.add(m.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } })
        }
        timerSeconds += ex.timer?.elapsedSeconds() ?: 0.0

        val validSets = ex.sets.filter { (it.reps.toIntOrNull() ?: 0) > 0 || (it.weight.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0.0 }
        val effectiveSets = if (validSets.isNotEmpty()) validSets else ex.sets
        val count = effectiveSets.size.coerceAtLeast(1)
        totalSets += count

        var bestSetScore = -1.0
        var bestKg = 0.0
        var bestReps = 0

        for (s in effectiveSets) {
            val reps = (s.reps.toIntOrNull() ?: 0).coerceAtLeast(if (validSets.isEmpty()) 8 else 0)
            val wRaw = s.weight.replace(',', '.').toDoubleOrNull() ?: 0.0
            val wKg = if ((s.weightUnit ?: WorkoutWeightUnit.KG) == WorkoutWeightUnit.LBS) wRaw / 2.2046226218 else wRaw
            totalVolumeKg += wKg * reps
            val score = wKg * (1.0 + reps / 30.0)
            if (score > bestSetScore) {
                bestSetScore = score
                bestKg = wKg
                bestReps = reps
            }
        }

        val prevBest = historicalMax1RmKg[ex.itemId] ?: 0.0
        val isPr = bestKg > 0.0 && bestSetScore > prevBest && prevBest > 0.0
        lifts.add(
            ShareLiftItem(
                name = ex.name,
                setCount = count,
                bestKg = bestKg,
                bestReps = bestReps,
                isPr = isPr,
                est1RmKg = bestSetScore.coerceAtLeast(0.0)
            )
        )
    }

    val durationMins = if (timerSeconds >= 120.0) {
        (timerSeconds / 60.0).roundToInt()
    } else {
        (totalSets * 3).coerceIn(20, 120)
    }
    val durationLabel = if (durationMins >= 60) "${durationMins / 60}h ${durationMins % 60}m" else "${durationMins}m"

    val title = when {
        muscleSet.isEmpty() -> "STRENGTH SESSION"
        muscleSet.size == 1 -> "${muscleSet.first().uppercase()} SESSION"
        else -> muscleSet.take(2).joinToString(" & ").uppercase()
    }

    val dateLabel = if (selectedDate == LocalDate.now()) {
        "Today"
    } else {
        selectedDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))
    }

    val prItems = lifts.filter { it.isPr }
    val topLift = (prItems.maxByOrNull { it.est1RmKg } ?: lifts.maxByOrNull { it.est1RmKg })
    val kcal = caloriesBurned ?: (durationMins * 7).coerceAtLeast(140)

    return WorkoutShareSummary(
        sessionTitle = title,
        dateLabel = dateLabel,
        durationLabel = durationLabel,
        totalSets = totalSets,
        totalVolumeKg = totalVolumeKg,
        caloriesKcal = kcal,
        lifts = lifts,
        prCount = prItems.size,
        topPrLift = topLift,
        muscles = muscleSet.toList()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutShareSheet(
    visible: Boolean,
    selectedDate: LocalDate,
    exercises: List<PlannedExercise>,
    caloriesBurned: Int?,
    defaultUnit: WorkoutWeightUnit,
    persistedState: WorkoutPersistedState,
    onDismiss: () -> Unit
) {
    if (!visible || exercises.isEmpty()) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val graphicsLayer = rememberGraphicsLayer()

    var format by remember { mutableStateOf(ShareCardFormat.STORY) }
    var theme by remember { mutableStateOf(ShareCardTheme.TACTICAL) }
    var unit by remember(defaultUnit) { mutableStateOf(defaultUnit) }
    var showPR by remember { mutableStateOf(true) }
    var showLifts by remember { mutableStateOf(true) }
    var showMuscles by remember { mutableStateOf(true) }
    var showCalories by remember { mutableStateOf(true) }
    var showSocials by remember { mutableStateOf(true) }
    var useFloatingEmblem by remember { mutableStateOf(false) }
    var hudOffsetY by remember { mutableFloatStateOf(0f) }
    var customPhotoBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var exportTransparentHudOnly by remember { mutableStateOf(false) }

    val summary = remember(selectedDate, exercises, caloriesBurned, persistedState) {
        buildWorkoutShareSummary(selectedDate, exercises, caloriesBurned, persistedState)
    }

    // Prepare keyed transparent-alpha APEX emblem once on background thread
    var alphaLogoBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(context) {
        alphaLogoBitmap = withContext(Dispatchers.Default) { loadAlphaKeyedApexLogo(context) }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }
                if (bmp != null) customPhotoBitmap = bmp.asImageBitmap()
            }
        }
    }

    fun captureAndDispatch(targetPackage: String?, saveToGallery: Boolean = false) {
        scope.launch {
            runCatching {
                val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                if (saveToGallery) {
                    saveBitmapToGallery(context, bitmap)
                    Toast.makeText(context, "Saved APEX Share Card to Gallery", Toast.LENGTH_SHORT).show()
                } else {
                    val uri = writeShareCachePng(context, bitmap)
                    shareImageUri(context, uri, targetPackage)
                }
            }.onFailure {
                Toast.makeText(context, "Unable to export image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF131724),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(APEXVectorIcons.Share2, contentDescription = null, tint = Color(0xFFFF2E2E), modifier = Modifier.size(19.dp))
                    Column {
                        Text("Share Workout", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        Text(format.subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 11.5.sp)
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.09f))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Done", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // LIVE CAPTUREABLE SHARE CARD STAGE
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                WorkoutShareCardCanvas(
                    summary = summary,
                    format = format,
                    theme = theme,
                    unit = unit,
                    showPR = showPR,
                    showLifts = showLifts,
                    showMuscles = showMuscles,
                    showCalories = showCalories,
                    showSocials = showSocials,
                    useFloatingEmblem = useFloatingEmblem,
                    alphaLogo = alphaLogoBitmap,
                    customPhoto = customPhotoBitmap,
                    transparentHudExport = exportTransparentHudOnly && format == ShareCardFormat.HUD,
                    hudOffsetY = hudOffsetY,
                    onDragHud = { delta -> hudOffsetY = (hudOffsetY + delta).coerceIn(-220f, 30f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawWithContent {
                            graphicsLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            drawLayer(graphicsLayer)
                        }
                )
            }

            // Share Action Buttons (Instagram Story, Facebook, Save PNG, System Share)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CrimsonActionBtn(
                    label = "Instagram Story",
                    iconRes = R.drawable.social_instagram,
                    onClick = { captureAndDispatch("com.instagram.android") },
                    modifier = Modifier.weight(1f)
                )
                CrimsonActionBtn(
                    label = "Facebook",
                    iconRes = R.drawable.social_facebook,
                    onClick = { captureAndDispatch("com.facebook.katana") },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SecondaryGlassBtn(
                    label = "Save High-Res PNG",
                    icon = APEXVectorIcons.Download,
                    onClick = { captureAndDispatch(null, saveToGallery = true) },
                    modifier = Modifier.weight(1f)
                )
                SecondaryGlassBtn(
                    label = "More Apps",
                    icon = APEXVectorIcons.Share2,
                    onClick = { captureAndDispatch(null, saveToGallery = false) },
                    modifier = Modifier.weight(1f)
                )
            }

            // 1. Format Selector
            SectionCard("1. Share Format") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormatTile(ShareCardFormat.STORY, format == ShareCardFormat.STORY, { format = ShareCardFormat.STORY }, Modifier.weight(1f))
                    FormatTile(ShareCardFormat.HUD, format == ShareCardFormat.HUD, { format = ShareCardFormat.HUD }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormatTile(ShareCardFormat.SQUARE, format == ShareCardFormat.SQUARE, { format = ShareCardFormat.SQUARE }, Modifier.weight(1f))
                    FormatTile(ShareCardFormat.PR, format == ShareCardFormat.PR, { format = ShareCardFormat.PR }, Modifier.weight(1f))
                }

                if (format == ShareCardFormat.HUD) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(Color(0x24E10600))
                            .border(1.dp, Color(0x66FF2E2E), RoundedCornerShape(13.dp))
                            .padding(11.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Gym Photo Backdrop", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Upload photo or toggle transparent PNG HUD", color = Color.White.copy(alpha = 0.65f), fontSize = 10.5.sp)
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.horizontalGradient(listOf(Color(0xFFE10600), Color(0xFFFF2E2E))))
                                .clickable {
                                    exportTransparentHudOnly = false
                                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                                .padding(horizontal = 11.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(APEXVectorIcons.ImageOverlay, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                            Text("Choose Photo", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    ShareToggleRow("Export Transparent HUD Only (No Background)", exportTransparentHudOnly) {
                        exportTransparentHudOnly = !exportTransparentHudOnly
                    }
                }
            }

            // 2. Theme & Units
            SectionCard("2. Visual Theme & Units") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShareCardTheme.entries.forEach { t ->
                        val active = theme == t
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) Color(0x38E10600) else Color.White.copy(alpha = 0.05f))
                                .border(1.dp, if (active) Color(0xFFFF2E2E) else Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                                .clickable { theme = t }
                                .padding(10.dp)
                        ) {
                            Text(t.title, color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(t.subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 9.5.sp, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UnitChip("Metric (KG)", unit == WorkoutWeightUnit.KG, { unit = WorkoutWeightUnit.KG }, Modifier.weight(1f))
                    UnitChip("Imperial (LB)", unit == WorkoutWeightUnit.LBS, { unit = WorkoutWeightUnit.LBS }, Modifier.weight(1f))
                }
            }

            // 3. Visible Elements
            SectionCard("3. Customize Card Elements") {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    ShareToggleRow("PR Record Banner", showPR) { showPR = !showPR }
                    ShareToggleRow("Top Lifts Breakdown", showLifts) { showLifts = !showLifts }
                    ShareToggleRow("Muscles Worked Tags", showMuscles) { showMuscles = !showMuscles }
                    ShareToggleRow("Active Energy (kcal)", showCalories) { showCalories = !showCalories }
                    ShareToggleRow("@apex.fg & @apexfitnessgym Footer", showSocials) { showSocials = !showSocials }
                    ShareToggleRow("Floating Emblem Logo Style", useFloatingEmblem) { useFloatingEmblem = !useFloatingEmblem }
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun WorkoutShareCardCanvas(
    summary: WorkoutShareSummary,
    format: ShareCardFormat,
    theme: ShareCardTheme,
    unit: WorkoutWeightUnit,
    showPR: Boolean,
    showLifts: Boolean,
    showMuscles: Boolean,
    showCalories: Boolean,
    showSocials: Boolean,
    useFloatingEmblem: Boolean,
    alphaLogo: ImageBitmap?,
    customPhoto: ImageBitmap?,
    transparentHudExport: Boolean,
    hudOffsetY: Float,
    onDragHud: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSquare = format == ShareCardFormat.SQUARE
    val isHud = format == ShareCardFormat.HUD
    val isPrSpotlight = format == ShareCardFormat.PR

    val bgBrush = when (theme) {
        ShareCardTheme.TACTICAL -> Brush.linearGradient(
            colors = listOf(Color(0xFF1A2033), Color(0xFF0E111A), Color(0xFF191D2C)),
            start = Offset(0f, 0f),
            end = Offset(900f, 1400f)
        )
        ShareCardTheme.CRIMSON -> Brush.linearGradient(
            colors = listOf(Color(0xFF3A0F15), Color(0xFF14090E), Color(0xFF240C13)),
            start = Offset(0f, 0f),
            end = Offset(900f, 1400f)
        )
        ShareCardTheme.STEALTH -> Brush.verticalGradient(
            colors = listOf(Color(0xFF181C27), Color(0xFF10131C))
        )
    }

    val aspect = if (isSquare) 1f else (9f / 15.2f)
    val outerPad = if (isSquare) 13.dp else 18.dp

    Box(
        modifier = modifier
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(24.dp))
            .then(
                if (transparentHudExport) Modifier.background(Color.Transparent)
                else Modifier
                    .background(bgBrush)
                    .border(
                        width = 1.dp,
                        color = if (theme == ShareCardTheme.CRIMSON) Color(0x55FF3C3C) else Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(24.dp)
                    )
            )
            .drawBehind {
                if (!transparentHudExport && !isHud) {
                    // Subtle crimson glow + tactical grid
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                if (theme == ShareCardTheme.CRIMSON) Color(0x55FF2E2E) else Color(0x38E10600),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.82f, size.height * 0.12f),
                            radius = size.width * 0.65f
                        ),
                        radius = size.width * 0.65f,
                        center = Offset(size.width * 0.82f, size.height * 0.12f)
                    )
                }
            }
    ) {
        if (isHud) {
            if (!transparentHudExport) {
                if (customPhoto != null) {
                    Image(
                        bitmap = customPhoto,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0x330A0C12), Color(0xB80A0C12))
                                )
                            )
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0x38E10600), Color(0xFF121622)),
                                    radius = 700f
                                )
                            )
                    )
                }
            }

            // Draggable Frosted Photo HUD Panel
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(14.dp)
                    .offset { IntOffset(0, hudOffsetY.roundToInt()) }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount -> onDragHud(dragAmount) }
                    }
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x9E0E111A))
                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(20.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShareBrandHeader(summary.dateLabel, isCompact = false, useFloatingEmblem = true, alphaLogo = alphaLogo)
                Text(summary.sessionTitle, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ShareStatsGrid(summary, unit, isCompact = false)
                if (showLifts && summary.lifts.isNotEmpty()) {
                    ShareTopLiftsBox(summary.lifts.take(3), unit, isCompact = false)
                }
                if (showSocials) ShareSocialFooter(isCompact = false)
            }
            return@Box
        }

        if (isPrSpotlight) {
            val topLift = summary.topPrLift
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(outerPad),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                ShareBrandHeader(summary.dateLabel, isCompact = false, useFloatingEmblem = useFloatingEmblem, alphaLogo = alphaLogo)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(Color(0xFFE10600), Color(0xFFFF2E2E))))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(APEXVectorIcons.Trophy, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Text("PERSONAL RECORD SPOTLIGHT", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                    Text(
                        text = (topLift?.name ?: summary.sessionTitle).uppercase(),
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                    Text(
                        text = if (topLift != null && topLift.bestKg > 0.0) {
                            "${formatLoad(topLift.bestKg, unit).uppercase()} x ${topLift.bestReps}"
                        } else {
                            formatVolume(summary.totalVolumeKg, unit).uppercase()
                        },
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black
                    )
                    if (topLift != null && topLift.est1RmKg > 0.0) {
                        Text(
                            text = "Est. 1RM: ${formatLoad(topLift.est1RmKg, unit)}",
                            color = Color(0xFFFF5252),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    ShareStatsGrid(summary, unit, isCompact = false)
                }

                if (showSocials) ShareSocialFooter(isCompact = false) else Spacer(Modifier.height(2.dp))
            }
            return@Box
        }

        // Standard 9:16 Story Card OR Precision-Fitted 1:1 Square Feed Card (0px clipping!)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(outerPad),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = if (isSquare) Arrangement.SpaceBetween else Arrangement.spacedBy(10.dp)
            ) {
                ShareBrandHeader(summary.dateLabel, isCompact = isSquare, useFloatingEmblem = useFloatingEmblem, alphaLogo = alphaLogo)

                // Title + PR Banner Block
                Column(verticalArrangement = Arrangement.spacedBy(if (isSquare) 4.dp else 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(APEXVectorIcons.Zap, contentDescription = null, tint = Color(0xFFFF3B3B), modifier = Modifier.size(if (isSquare) 11.dp else 13.dp))
                        Text(
                            text = "STRENGTH SESSION COMPLETE",
                            color = Color(0xFFFF3B3B),
                            fontSize = if (isSquare) 8.5.sp else 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = summary.sessionTitle,
                        color = Color.White,
                        fontSize = if (isSquare) 16.5.sp else 21.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (showPR && summary.topPrLift != null) {
                        val prLift = summary.topPrLift
                        val badgeLabel = if (summary.prCount > 0) "${summary.prCount} NEW PR${if (summary.prCount > 1) "S" else ""}" else "TOP SET"
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(if (isSquare) 8.dp else 10.dp))
                                .background(Brush.horizontalGradient(listOf(Color(0xEBE10600), Color(0xD9FF2E2E))))
                                .padding(horizontal = if (isSquare) 9.dp else 11.dp, vertical = if (isSquare) 4.dp else 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(APEXVectorIcons.Trophy, contentDescription = null, tint = Color.White, modifier = Modifier.size(if (isSquare) 11.dp else 13.dp))
                            Text(
                                text = "$badgeLabel  |  ${prLift.name} • ${formatLoad(prLift.bestKg, unit)} x ${prLift.bestReps}",
                                color = Color.White,
                                fontSize = if (isSquare) 9.5.sp else 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                ShareStatsGrid(summary, unit, isCompact = isSquare)

                if (showLifts && summary.lifts.isNotEmpty()) {
                    ShareTopLiftsBox(summary.lifts.take(if (isSquare) 2 else 4), unit, isCompact = isSquare)
                }

                if ((showMuscles && summary.muscles.isNotEmpty()) || showCalories) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (showMuscles) {
                                summary.muscles.take(if (isSquare) 3 else 4).forEach { m ->
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(Color(0x2EE10600))
                                            .border(1.dp, Color(0x59FF2E2E), CircleShape)
                                            .padding(horizontal = if (isSquare) 7.dp else 8.dp, vertical = if (isSquare) 2.5.dp else 4.dp)
                                    ) {
                                        Text(
                                            text = m,
                                            color = Color(0xFFFFD6D6),
                                            fontSize = if (isSquare) 9.sp else 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                        if (showCalories) {
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                                    .padding(horizontal = if (isSquare) 7.dp else 9.dp, vertical = if (isSquare) 2.5.dp else 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(APEXVectorIcons.Flame, contentDescription = null, tint = Color(0xFFFF4D4D), modifier = Modifier.size(if (isSquare) 10.dp else 12.dp))
                                Text(
                                    text = "${summary.caloriesKcal} kcal",
                                    color = Color.White,
                                    fontSize = if (isSquare) 9.5.sp else 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            if (showSocials) {
                Spacer(Modifier.height(if (isSquare) 5.dp else 8.dp))
                ShareSocialFooter(isCompact = isSquare)
            }
        }
    }
}

@Composable
private fun ShareBrandHeader(
    dateLabel: String,
    isCompact: Boolean,
    useFloatingEmblem: Boolean,
    alphaLogo: ImageBitmap?
) {
    val logoSize = if (isCompact) 34.dp else 44.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 10.dp)) {
            if (useFloatingEmblem && alphaLogo != null) {
                Image(
                    bitmap = alphaLogo,
                    contentDescription = "APEX Fitness Gym",
                    modifier = Modifier.size(logoSize + 2.dp)
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = "APEX Fitness Gym",
                    modifier = Modifier
                        .size(logoSize)
                        .clip(RoundedCornerShape(if (isCompact) 9.dp else 11.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(if (isCompact) 9.dp else 11.dp))
                )
            }
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("APEX", color = Color(0xFFFF2E2E), fontSize = if (isCompact) 11.5.sp else 13.sp, fontWeight = FontWeight.Black, letterSpacing = 0.9.sp)
                    Text("FITNESS GYM", color = Color.White, fontSize = if (isCompact) 11.5.sp else 13.sp, fontWeight = FontWeight.Black, letterSpacing = 0.9.sp)
                }
                Text(
                    text = "THE PEAK OF STRENGTH",
                    color = Color.White.copy(alpha = 0.60f),
                    fontSize = if (isCompact) 8.5.sp else 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                )
            }
        }
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                .padding(horizontal = if (isCompact) 8.dp else 10.dp, vertical = if (isCompact) 3.5.dp else 5.dp)
        ) {
            Text(dateLabel, color = Color.White.copy(alpha = 0.88f), fontSize = if (isCompact) 9.sp else 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ShareStatsGrid(summary: WorkoutShareSummary, unit: WorkoutWeightUnit, isCompact: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 6.dp else 8.dp)
    ) {
        ShareStatBox(summary.durationLabel, "DURATION", APEXVectorIcons.Timer, isCompact, Modifier.weight(1f))
        ShareStatBox(formatVolume(summary.totalVolumeKg, unit), "VOLUME", APEXVectorIcons.Dumbbell, isCompact, Modifier.weight(1f))
        ShareStatBox("${summary.totalSets}", "SETS", APEXVectorIcons.Layers, isCompact, Modifier.weight(1f))
    }
}

@Composable
private fun ShareStatBox(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(if (isCompact) 10.dp else 14.dp))
            .background(Color.White.copy(alpha = 0.065f))
            .border(1.dp, Color.White.copy(alpha = 0.11f), RoundedCornerShape(if (isCompact) 10.dp else 14.dp))
            .padding(horizontal = 6.dp, vertical = if (isCompact) 6.dp else 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = Color.White, fontSize = if (isCompact) 13.sp else 16.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFFFF4D4D), modifier = Modifier.size(if (isCompact) 10.dp else 12.dp))
            Text(label, color = Color.White.copy(alpha = 0.58f), fontSize = if (isCompact) 8.5.sp else 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ShareTopLiftsBox(lifts: List<ShareLiftItem>, unit: WorkoutWeightUnit, isCompact: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (isCompact) 11.dp else 15.dp))
            .background(Color(0x990F121C))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(if (isCompact) 11.dp else 15.dp))
            .padding(horizontal = if (isCompact) 10.dp else 12.dp, vertical = if (isCompact) 7.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(APEXVectorIcons.Dumbbell, contentDescription = null, tint = Color(0xFFFF4D4D), modifier = Modifier.size(if (isCompact) 10.dp else 12.dp))
                Text("TOP LIFTS", color = Color.White.copy(alpha = 0.52f), fontSize = if (isCompact) 8.5.sp else 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            }
            Text("BEST SET", color = Color.White.copy(alpha = 0.52f), fontSize = if (isCompact) 8.5.sp else 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        }
        lifts.forEachIndexed { idx, lift ->
            if (idx > 0) HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f).padding(end = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0x3FE10600))
                            .border(1.dp, Color(0x66FF2E2E), RoundedCornerShape(5.dp))
                            .padding(horizontal = 4.5.dp, vertical = 1.dp)
                    ) {
                        Text("${lift.setCount}x", color = Color(0xFFFF6B6B), fontSize = if (isCompact) 9.sp else 10.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text(
                        text = lift.name,
                        color = Color.White,
                        fontSize = if (isCompact) 10.8.sp else 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val bestText = if (lift.bestKg > 0.0) "${formatLoad(lift.bestKg, unit)} x ${lift.bestReps}" else "${lift.bestReps} reps"
                    Text(bestText, color = Color.White.copy(alpha = 0.92f), fontSize = if (isCompact) 10.5.sp else 11.5.sp, fontWeight = FontWeight.ExtraBold)
                    if (lift.isPr) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0x3DE10600))
                                .border(1.dp, Color(0x73FF4D4D), RoundedCornerShape(5.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(APEXVectorIcons.Award, contentDescription = null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(if (isCompact) 9.dp else 10.dp))
                            Text("PR", color = Color(0xFFFF6B6B), fontSize = if (isCompact) 8.5.sp else 9.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareSocialFooter(isCompact: Boolean) {
    Column {
        HorizontalDivider(color = Color.White.copy(alpha = 0.10f), thickness = 0.7.dp)
        Spacer(Modifier.height(if (isCompact) 5.dp else 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.social_instagram),
                        contentDescription = null,
                        tint = Color(0xFFFF5C7C),
                        modifier = Modifier.size(if (isCompact) 11.dp else 13.dp)
                    )
                    Text("@apex.fg", color = Color.White.copy(alpha = 0.88f), fontSize = if (isCompact) 9.2.sp else 10.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.social_facebook),
                        contentDescription = null,
                        tint = Color(0xFF58A6FF),
                        modifier = Modifier.size(if (isCompact) 11.dp else 13.dp)
                    )
                    Text("@apexfitnessgym", color = Color.White.copy(alpha = 0.88f), fontSize = if (isCompact) 9.2.sp else 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("APEX v2.5.8", color = Color.White.copy(alpha = 0.48f), fontSize = if (isCompact) 8.5.sp else 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
        }
    }
}

@Composable
private fun CrimsonActionBtn(label: String, iconRes: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFFE10600), Color(0xFFFF2E2E))))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(iconRes), contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
private fun SecondaryGlassBtn(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xB81A1F2E))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Text(
            text = title.uppercase(),
            color = Color.White.copy(alpha = 0.60f),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.9.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        content()
    }
}

@Composable
private fun FormatTile(fmt: ShareCardFormat, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (active) Color(0x38E10600) else Color.White.copy(alpha = 0.05f))
            .border(1.dp, if (active) Color(0xFFFF2E2E) else Color.White.copy(alpha = 0.10f), RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(11.dp)
    ) {
        Text(fmt.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(fmt.subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun UnitChip(label: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (active) Color(0x38E10600) else Color.White.copy(alpha = 0.05f))
            .border(1.dp, if (active) Color(0xFFFF2E2E) else Color.White.copy(alpha = 0.10f), RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ShareToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(22.dp)
                .clip(CircleShape)
                .background(if (checked) Color(0xFFE10600) else Color.White.copy(alpha = 0.18f))
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(Color.White))
        }
    }
}

private fun formatVolume(volumeKg: Double, unit: WorkoutWeightUnit): String {
    val value = if (unit == WorkoutWeightUnit.KG) volumeKg else volumeKg * 2.2046226218
    val suffix = if (unit == WorkoutWeightUnit.KG) "kg" else "lb"
    return "${NumberFormat.getIntegerInstance(Locale.US).format(value.roundToInt())} $suffix"
}

private fun formatLoad(kg: Double, unit: WorkoutWeightUnit): String {
    val v = if (unit == WorkoutWeightUnit.KG) kg else kg * 2.2046226218
    val suffix = if (unit == WorkoutWeightUnit.KG) "kg" else "lb"
    val rounded = if (v % 1.0 < 0.15 || v % 1.0 > 0.85) {
        v.roundToInt().toString()
    } else {
        String.format(Locale.US, "%.1f", v)
    }
    return "$rounded $suffix"
}

private fun loadAlphaKeyedApexLogo(context: Context): ImageBitmap? {
    val src = BitmapFactory.decodeResource(context.resources, R.drawable.ic_logo) ?: return null
    val scaled = Bitmap.createScaledBitmap(src, 220, 220, true)
    val out = scaled.copy(Bitmap.Config.ARGB_8888, true)
    val w = out.width
    val h = out.height
    val pixels = IntArray(w * h)
    out.getPixels(pixels, 0, w, 0, 0, w, h)
    for (i in pixels.indices) {
        val c = pixels[i]
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        val lum = max(r, max(g, b))
        val alpha = (((lum - 18f) / 38f).coerceIn(0f, 1f) * 255f).roundToInt()
        pixels[i] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
    }
    out.setPixels(pixels, 0, w, 0, 0, w, h)
    return out.asImageBitmap()
}

private suspend fun writeShareCachePng(context: Context, bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, "workout_share").apply { mkdirs() }
    val file = File(dir, "apex_workout_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap) = withContext(Dispatchers.IO) {
    val filename = "APEX_Workout_${System.currentTimeMillis()}.png"
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/APEX")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext
    resolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }
}

private fun shareImageUri(context: Context, uri: Uri, targetPackage: String?) {
    if (targetPackage == "com.instagram.android") {
        val igStoryIntent = Intent("com.instagram.share.ADD_TO_STORY").apply {
            setDataAndType(uri, "image/png")
            putExtra("interactive_asset_uri", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage("com.instagram.android")
        }
        context.grantUriPermission("com.instagram.android", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (igStoryIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(igStoryIntent)
            return
        }
    }

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (targetPackage != null) setPackage(targetPackage)
    }
    if (targetPackage != null && sendIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(sendIntent)
    } else {
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(fallback, "Share APEX Workout"))
    }
}
