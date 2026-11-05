package com.apex.app.ui.workouts

import android.content.Context
import android.graphics.RectF
import android.graphics.Region
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.PathParser
import com.apex.app.data.ExerciseItem
import com.apex.app.models.Gender
import com.apex.app.models.WorkoutDate
import com.apex.app.models.WorkoutPersistedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Metadata mapping each anatomical SVG slug to APEX's 317-exercise catalog muscle names
 * and natural synergist muscle groups.
 */
internal data class MuscleRegionMeta(
    val slug: String,
    val shortLabel: String,
    val fullLabel: String,
    val catalogMuscles: List<String>,
    val synergistSlugs: List<String>,
    val recoveryWindowHours: Int
)

internal val APEX_MUSCLE_REGIONS: Map<String, MuscleRegionMeta> = linkedMapOf(
    "chest" to MuscleRegionMeta("chest", "Chest", "Chest (Pectorals)", listOf("chest"), listOf("deltoids", "triceps"), 48),
    "deltoids" to MuscleRegionMeta("deltoids", "Shoulders", "Shoulders (Deltoids)", listOf("shoulders"), listOf("trapezius", "triceps"), 36),
    "biceps" to MuscleRegionMeta("biceps", "Biceps", "Biceps Brachii", listOf("biceps"), listOf("forearm"), 36),
    "triceps" to MuscleRegionMeta("triceps", "Triceps", "Triceps Brachii", listOf("triceps"), listOf("deltoids"), 36),
    "forearm" to MuscleRegionMeta("forearm", "Forearms", "Forearms & Grip", listOf("forearms"), listOf("biceps"), 24),
    "abs" to MuscleRegionMeta("abs", "Abs", "Abs (Rectus Abdominis)", listOf("abdominals"), listOf("obliques"), 24),
    "obliques" to MuscleRegionMeta("obliques", "Obliques", "Obliques & Core", listOf("abdominals"), listOf("abs"), 24),
    "quadriceps" to MuscleRegionMeta("quadriceps", "Quads", "Quadriceps", listOf("quadriceps"), listOf("gluteal", "calves"), 60),
    "adductors" to MuscleRegionMeta("adductors", "Adductors", "Inner & Outer Thigh", listOf("adductors", "abductors"), listOf("gluteal"), 48),
    "tibialis" to MuscleRegionMeta("tibialis", "Tibialis", "Tibialis & Lower Leg", listOf("calves"), listOf("calves"), 24),
    "calves" to MuscleRegionMeta("calves", "Calves", "Calves (Gastrocnemius)", listOf("calves"), listOf("tibialis"), 36),
    "trapezius" to MuscleRegionMeta("trapezius", "Traps", "Trapezius (Upper Back)", listOf("traps"), listOf("deltoids", "neck"), 36),
    "upper-back" to MuscleRegionMeta("upper-back", "Lats & Back", "Lats & Middle Back", listOf("lats", "middle back"), listOf("biceps", "trapezius"), 48),
    "lower-back" to MuscleRegionMeta("lower-back", "Lower Back", "Lower Back (Erectors)", listOf("lower back"), listOf("gluteal", "hamstring"), 48),
    "gluteal" to MuscleRegionMeta("gluteal", "Glutes", "Glutes (Maximus & Medius)", listOf("glutes"), listOf("hamstring", "quadriceps"), 48),
    "hamstring" to MuscleRegionMeta("hamstring", "Hamstrings", "Hamstrings", listOf("hamstrings"), listOf("gluteal", "calves"), 48),
    "neck" to MuscleRegionMeta("neck", "Neck", "Neck Flexors", listOf("neck"), listOf("trapezius"), 24)
)

private val INERT_ANATOMY_SLUGS = setOf("head", "hair", "hands", "feet", "knees", "ankles")

internal data class ParsedMusclePath(
    val slug: String,
    val isInert: Boolean,
    val composePath: Path,
    val hitRegion: Region
)

internal data class ParsedBodySide(
    val viewBoxX: Float,
    val viewBoxY: Float,
    val viewBoxW: Float,
    val viewBoxH: Float,
    val muscles: List<ParsedMusclePath>,
    val outlines: List<Path>
)

internal data class ParsedBodyDataset(
    val maleFront: ParsedBodySide,
    val maleBack: ParsedBodySide,
    val femaleFront: ParsedBodySide,
    val femaleBack: ParsedBodySide
)

internal object BodyAnatomyAssetLoader {
    @Volatile
    private var cached: ParsedBodyDataset? = null

    fun load(context: Context): ParsedBodyDataset {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val raw = context.assets.open("body_anatomy_paths.json").bufferedReader().use { it.readText() }
            val root = JSONObject(raw)
            val parsed = ParsedBodyDataset(
                maleFront = parseSide(root.getJSONObject("male"), "front", "frontViewBox"),
                maleBack = parseSide(root.getJSONObject("male"), "back", "backViewBox"),
                femaleFront = parseSide(root.getJSONObject("female"), "front", "frontViewBox"),
                femaleBack = parseSide(root.getJSONObject("female"), "back", "backViewBox")
            )
            cached = parsed
            return parsed
        }
    }

    private fun parseSide(genderObj: JSONObject, sideKey: String, vbKey: String): ParsedBodySide {
        val vbParts = genderObj.getString(vbKey).trim().split(Regex("\\s+")).map { it.toFloat() }
        val vx = vbParts[0]
        val vy = vbParts[1]
        val vw = vbParts[2]
        val vh = vbParts[3]
        val clipRegion = Region(
            (vx - 200f).roundToInt(),
            (vy - 200f).roundToInt(),
            (vx + vw + 200f).roundToInt(),
            (vy + vh + 200f).roundToInt()
        )

        val muscles = mutableListOf<ParsedMusclePath>()
        val arr = genderObj.getJSONArray(sideKey)
        for (i in 0 until arr.length()) {
            val part = arr.getJSONObject(i)
            val slug = part.getString("slug")
            val isInert = slug in INERT_ANATOMY_SLUGS || !APEX_MUSCLE_REGIONS.containsKey(slug)
            val pathObj = part.getJSONObject("path")
            for (groupKey in listOf("common", "left", "right")) {
                if (!pathObj.has(groupKey)) continue
                val dArr = pathObj.getJSONArray(groupKey)
                for (j in 0 until dArr.length()) {
                    val d = dArr.getString(j)
                    val androidPath = runCatching { PathParser.createPathFromPathData(d) }.getOrNull() ?: continue
                    val region = Region()
                    if (!isInert) {
                        val bounds = RectF()
                        androidPath.computeBounds(bounds, true)
                        region.setPath(androidPath, clipRegion)
                    }
                    muscles.add(
                        ParsedMusclePath(
                            slug = slug,
                            isInert = isInert,
                            composePath = androidPath.asComposePath(),
                            hitRegion = region
                        )
                    )
                }
            }
        }

        val outlines = mutableListOf<Path>()
        val outlinesObj = genderObj.optJSONObject("outlines")
        val outArr = outlinesObj?.optJSONArray(sideKey)
        if (outArr != null) {
            for (i in 0 until outArr.length()) {
                val d = outArr.getString(i)
                val p = runCatching { PathParser.createPathFromPathData(d) }.getOrNull() ?: continue
                outlines.add(p.asComposePath())
            }
        }

        return ParsedBodySide(vx, vy, vw, vh, muscles, outlines)
    }
}

internal data class MuscleRecoveryStat(
    val slug: String,
    val meta: MuscleRegionMeta,
    val weeklySets: Int,
    val hoursRemaining: Int
)

internal fun computeMuscleRecoveryStats(
    workoutState: WorkoutPersistedState,
    today: LocalDate = LocalDate.now()
): Map<String, MuscleRecoveryStat> {
    val setsPerCatalog = mutableMapOf<String, Int>()
    val latestDayPerCatalog = mutableMapOf<String, LocalDate>()

    for ((dateKey, plan) in workoutState.dayPlans) {
        val d = WorkoutDate.parse(dateKey) ?: continue
        val daysAgo = ChronoUnit.DAYS.between(d, today)
        if (daysAgo !in 0..6) continue
        for (ex in plan.exercises) {
            val performed = ex.sets.count { it.reps.isNotBlank() || it.weight.isNotBlank() }.coerceAtLeast(
                if (ex.sets.isNotEmpty() && daysAgo == 0L) 0 else 0
            )
            if (performed <= 0) continue
            for (m in ex.primaryMuscles) {
                val key = m.lowercase()
                setsPerCatalog[key] = (setsPerCatalog[key] ?: 0) + performed
                val prev = latestDayPerCatalog[key]
                if (prev == null || d.isAfter(prev)) latestDayPerCatalog[key] = d
            }
            for (m in ex.secondaryMuscles) {
                val key = m.lowercase()
                setsPerCatalog[key] = (setsPerCatalog[key] ?: 0) + (performed / 2).coerceAtLeast(1)
            }
        }
    }

    val out = linkedMapOf<String, MuscleRecoveryStat>()
    for ((slug, meta) in APEX_MUSCLE_REGIONS) {
        val sets = meta.catalogMuscles.sumOf { setsPerCatalog[it] ?: 0 }
        val lastTrained = meta.catalogMuscles.mapNotNull { latestDayPerCatalog[it] }.maxOrNull()
        val hrsLeft = if (lastTrained == null) {
            0
        } else {
            val elapsedHours = (ChronoUnit.DAYS.between(lastTrained, today).toInt() * 24).coerceAtLeast(6)
            (meta.recoveryWindowHours - elapsedHours).coerceAtLeast(0)
        }
        out[slug] = MuscleRecoveryStat(slug, meta, sets, hrsLeft)
    }
    return out
}

internal fun slugsFromCatalogMuscles(primaryMuscles: Set<String>): Set<String> {
    if (primaryMuscles.isEmpty()) return emptySet()
    val lowered = primaryMuscles.map { it.lowercase() }.toSet()
    return APEX_MUSCLE_REGIONS.values
        .filter { meta -> meta.catalogMuscles.any { it in lowered } }
        .mapTo(linkedSetOf()) { it.slug }
}

internal fun catalogMusclesFromSlugs(slugs: Set<String>): Set<String> =
    slugs.flatMapTo(linkedSetOf()) { slug -> APEX_MUSCLE_REGIONS[slug]?.catalogMuscles.orEmpty() }

enum class BodyMapMode { TARGET, HEATMAP }
enum class BodySideMode { BOTH, FRONT, BACK }

/**
 * Interactive 3D-Shaded Biometric Muscle Navigator card (embeddable inside Workouts Library
 * or displayed inside [WorkoutBodyMapBottomSheet]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BiometricBodyMapPanel(
    selectedSlugs: Set<String>,
    onSelectedSlugsChange: (Set<String>) -> Unit,
    workoutState: WorkoutPersistedState,
    defaultGender: Gender,
    matchedCount: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var dataset by remember { mutableStateOf<ParsedBodyDataset?>(null) }
    LaunchedEffect(context) {
        dataset = withContext(Dispatchers.Default) { BodyAnatomyAssetLoader.load(context) }
    }

    var mode by remember { mutableStateOf(BodyMapMode.TARGET) }
    var figureGender by remember(defaultGender) {
        mutableStateOf(if (defaultGender == Gender.FEMALE) Gender.FEMALE else Gender.MALE)
    }
    var sideMode by remember { mutableStateOf(BodySideMode.BOTH) }

    val recoveryStats = remember(workoutState.dayPlans) { computeMuscleRecoveryStats(workoutState) }

    val synergistSlugs = remember(selectedSlugs) {
        val syn = linkedSetOf<String>()
        selectedSlugs.forEach { s ->
            APEX_MUSCLE_REGIONS[s]?.synergistSlugs?.forEach { candidate ->
                if (candidate !in selectedSlugs) syn.add(candidate)
            }
        }
        syn
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xB8181D2C))
            .border(1.dp, Color.White.copy(alpha = 0.11f), RoundedCornerShape(24.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Row 1: Mode Switch + Male/Female Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SegmentedPillBar {
                SegmentedPillItem(
                    label = "Muscle Target",
                    icon = APEXVectorIcons.Target,
                    selected = mode == BodyMapMode.TARGET,
                    onClick = { mode = BodyMapMode.TARGET }
                )
                SegmentedPillItem(
                    label = "Heatmap",
                    icon = APEXVectorIcons.Flame,
                    selected = mode == BodyMapMode.HEATMAP,
                    onClick = { mode = BodyMapMode.HEATMAP }
                )
            }
            SegmentedPillBar {
                SegmentedPillItem(
                    label = "Male",
                    selected = figureGender != Gender.FEMALE,
                    onClick = { figureGender = Gender.MALE }
                )
                SegmentedPillItem(
                    label = "Female",
                    selected = figureGender == Gender.FEMALE,
                    onClick = { figureGender = Gender.FEMALE }
                )
            }
        }

        // Row 2: Front+Back | Front | Back + Split Presets
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SegmentedPillBar {
                SegmentedPillItem("Front + Back", selected = sideMode == BodySideMode.BOTH) { sideMode = BodySideMode.BOTH }
                SegmentedPillItem("Front", selected = sideMode == BodySideMode.FRONT) { sideMode = BodySideMode.FRONT }
                SegmentedPillItem("Back", selected = sideMode == BodySideMode.BACK) { sideMode = BodySideMode.BACK }
            }
            SplitQuickChip("Push") { onSelectedSlugsChange(setOf("chest", "deltoids", "triceps")) }
            SplitQuickChip("Pull") { onSelectedSlugsChange(setOf("upper-back", "trapezius", "biceps", "forearm")) }
            SplitQuickChip("Legs") { onSelectedSlugsChange(setOf("quadriceps", "hamstring", "gluteal", "calves")) }
            SplitQuickChip("Core") { onSelectedSlugsChange(setOf("abs", "obliques", "lower-back")) }
            if (selectedSlugs.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0x28E10600))
                        .border(1.dp, Color(0x66FF4D4D), CircleShape)
                        .clickable { onSelectedSlugsChange(emptySet()) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(APEXVectorIcons.RotateCcw, contentDescription = null, tint = Color(0xFFFF7070), modifier = Modifier.size(12.dp))
                    Text("Clear", color = Color(0xFFFF7070), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Biometric Tactical Scanner Stage
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x2EE10600), Color(0xCC0D1019)),
                        radius = 620f
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(20.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Legend Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        LegendDot(Color(0xFFFF2E2E), if (mode == BodyMapMode.HEATMAP) "High Vol" else "Primary")
                        LegendDot(Color(0xFFFF8A4C), if (mode == BodyMapMode.HEATMAP) "Mod Vol" else "Synergist")
                        LegendDot(Color(0xFF2B334B), "Rested")
                    }
                    Text(
                        text = if (mode == BodyMapMode.HEATMAP) "7-DAY VOLUME" else "TAP MUSCLE",
                        color = Color.White.copy(alpha = 0.50f),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.1.sp
                    )
                }

                val loaded = dataset
                if (loaded != null) {
                    val frontSide = if (figureGender == Gender.FEMALE) loaded.femaleFront else loaded.maleFront
                    val backSide = if (figureGender == Gender.FEMALE) loaded.femaleBack else loaded.maleBack

                    val onToggleSlug: (String) -> Unit = { slug ->
                        val next = if (slug in selectedSlugs) selectedSlugs - slug else selectedSlugs + slug
                        onSelectedSlugsChange(next)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (sideMode == BodySideMode.BOTH) 296.dp else 336.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (sideMode == BodySideMode.BOTH || sideMode == BodySideMode.FRONT) {
                            SculptedBodyFigureCanvas(
                                label = "ANTERIOR",
                                sideData = frontSide,
                                mode = mode,
                                primarySlugs = selectedSlugs,
                                synergistSlugs = synergistSlugs,
                                recoveryStats = recoveryStats,
                                onTapMuscle = onToggleSlug,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                        if (sideMode == BodySideMode.BOTH || sideMode == BodySideMode.BACK) {
                            SculptedBodyFigureCanvas(
                                label = "POSTERIOR",
                                sideData = backSide,
                                mode = mode,
                                primarySlugs = selectedSlugs,
                                synergistSlugs = synergistSlugs,
                                recoveryStats = recoveryStats,
                                onTapMuscle = onToggleSlug,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                } else {
                    Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                        Text("Loading anatomy...", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }

                // Bottom Biometric Readout Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xDD101420))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(APEXVectorIcons.Zap, contentDescription = null, tint = Color(0xFFFF2E2E), modifier = Modifier.size(14.dp))
                        val title = when {
                            selectedSlugs.isEmpty() -> "Full Body Catalog Active"
                            selectedSlugs.size == 1 -> APEX_MUSCLE_REGIONS[selectedSlugs.first()]?.fullLabel ?: "Selected Muscle"
                            else -> "${selectedSlugs.size} Muscle Regions Targeted"
                        }
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0x36E10600))
                            .border(1.dp, Color(0x66FF2E2E), CircleShape)
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "$matchedCount Workouts",
                            color = Color(0xFFFF8080),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // Selected Muscle Chips
        if (selectedSlugs.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                selectedSlugs.forEach { slug ->
                    val label = APEX_MUSCLE_REGIONS[slug]?.shortLabel ?: slug
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(Color(0x55E10600), Color(0x28FF2E2E))))
                            .border(1.dp, Color(0xFFFF3B3B), CircleShape)
                            .clickable { onSelectedSlugsChange(selectedSlugs - slug) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Icon(Icons.Filled.Close, contentDescription = "Remove $label", tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }

        // Recovery Clock Breakdown when in Heatmap Mode
        AnimatedVisibility(
            visible = mode == BodyMapMode.HEATMAP,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val sortedStats = remember(recoveryStats) {
                    recoveryStats.values.sortedByDescending { it.weeklySets }
                }
                val totalSets = sortedStats.sumOf { it.weeklySets }
                val readyCount = sortedStats.count { it.hoursRemaining == 0 }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeatmapKpiTile("Weekly Sets", "$totalSets", Color.White, Modifier.weight(1f))
                    HeatmapKpiTile("Ready", "$readyCount/${sortedStats.size}", Color(0xFF69DB7C), Modifier.weight(1f))
                }

                sortedStats.take(6).forEach { stat ->
                    val ready = stat.hoursRemaining == 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.045f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                            .clickable {
                                mode = BodyMapMode.TARGET
                                onSelectedSlugsChange(setOf(stat.slug))
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stat.meta.fullLabel, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (ready) APEXVectorIcons.ShieldCheck else APEXVectorIcons.Timer,
                                    contentDescription = null,
                                    tint = if (ready) Color(0xFF69DB7C) else Color(0xFFFF9E66),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = if (ready) "Peak Readiness • Ready" else "Recovering • ${stat.hoursRemaining}h left",
                                    color = if (ready) Color(0xFF69DB7C) else Color(0xFFFF9E66),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Text(
                            text = "${stat.weeklySets} sets",
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SculptedBodyFigureCanvas(
    label: String,
    sideData: ParsedBodySide,
    mode: BodyMapMode,
    primarySlugs: Set<String>,
    synergistSlugs: Set<String>,
    recoveryStats: Map<String, MuscleRecoveryStat>,
    onTapMuscle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.09f), CircleShape)
                .padding(horizontal = 9.dp, vertical = 2.5.dp)
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp
            )
        }

        val idleBrush = remember {
            Brush.linearGradient(
                colors = listOf(Color(0xFF353F5B), Color(0xFF242B3F), Color(0xFF1A1F2E)),
                start = Offset(0f, 0f),
                end = Offset(700f, 1400f)
            )
        }
        val inertBrush = remember {
            Brush.linearGradient(
                colors = listOf(Color(0xFF202638), Color(0xFF151926)),
                start = Offset(0f, 0f),
                end = Offset(0f, 1400f)
            )
        }
        val primaryBrush = remember {
            Brush.linearGradient(
                colors = listOf(Color(0xFFFF4A4A), Color(0xFFE10600), Color(0xFF990300)),
                start = Offset(150f, 150f),
                end = Offset(650f, 1300f)
            )
        }
        val secondaryBrush = remember {
            Brush.linearGradient(
                colors = listOf(Color(0xFFFF9E66), Color(0xFFD94E1F)),
                start = Offset(150f, 150f),
                end = Offset(650f, 1300f)
            )
        }
        val heatLowBrush = remember {
            Brush.linearGradient(
                colors = listOf(Color(0x99FFA05F), Color(0x88B95528)),
                start = Offset(150f, 150f),
                end = Offset(650f, 1300f)
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(sideData) {
                    detectTapGestures { tapOffset ->
                        val cw = size.width.toFloat()
                        val ch = size.height.toFloat()
                        if (cw <= 0f || ch <= 0f) return@detectTapGestures
                        val scale = min(cw / sideData.viewBoxW, ch / sideData.viewBoxH)
                        val dx = (cw - sideData.viewBoxW * scale) * 0.5f - sideData.viewBoxX * scale
                        val dy = (ch - sideData.viewBoxH * scale) * 0.5f - sideData.viewBoxY * scale
                        val vx = ((tapOffset.x - dx) / scale).roundToInt()
                        val vy = ((tapOffset.y - dy) / scale).roundToInt()

                        val hit = sideData.muscles.lastOrNull { !it.isInert && it.hitRegion.contains(vx, vy) }
                        if (hit != null) {
                            onTapMuscle(hit.slug)
                        }
                    }
                }
        ) {
            val cw = size.width
            val ch = size.height
            val scale = min(cw / sideData.viewBoxW, ch / sideData.viewBoxH)
            val dx = (cw - sideData.viewBoxW * scale) * 0.5f - sideData.viewBoxX * scale
            val dy = (ch - sideData.viewBoxH * scale) * 0.5f - sideData.viewBoxY * scale

            // Holographic floor pedestal ring beneath the athlete's feet
            val pedW = cw * 0.62f
            val pedH = 14.dp.toPx()
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x55E10600), Color.Transparent),
                    center = Offset(cw * 0.5f, ch - pedH * 0.5f),
                    radius = pedW * 0.55f
                ),
                topLeft = Offset((cw - pedW) * 0.5f, ch - pedH),
                size = Size(pedW, pedH)
            )

            withTransform({
                translate(dx, dy)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                for (m in sideData.muscles) {
                    if (m.isInert) {
                        drawPath(m.composePath, brush = inertBrush)
                        drawPath(m.composePath, color = Color(0xCC090C14), style = Stroke(width = 1.8f))
                    } else {
                        val isPri = m.slug in primarySlugs
                        val isSec = m.slug in synergistSlugs
                        val sets = recoveryStats[m.slug]?.weeklySets ?: 0

                        val fillBrush = if (mode == BodyMapMode.HEATMAP && !isPri) {
                            when {
                                sets >= 10 -> primaryBrush
                                sets >= 5 -> secondaryBrush
                                sets >= 1 -> heatLowBrush
                                else -> idleBrush
                            }
                        } else {
                            when {
                                isPri -> primaryBrush
                                isSec -> secondaryBrush
                                else -> idleBrush
                            }
                        }

                        val strokeColor = when {
                            isPri || (mode == BodyMapMode.HEATMAP && sets >= 10) -> Color(0xFFFFB0B0)
                            isSec || (mode == BodyMapMode.HEATMAP && sets >= 5) -> Color(0xD9FFB991)
                            else -> Color(0xE6090C14)
                        }

                        drawPath(m.composePath, brush = fillBrush)
                        drawPath(m.composePath, color = strokeColor, style = Stroke(width = if (isPri) 2.8f else 2.1f))
                    }
                }

                for (outline in sideData.outlines) {
                    drawPath(outline, color = Color.White.copy(alpha = 0.26f), style = Stroke(width = 2.2f))
                }
            }
        }
    }
}

/**
 * Full-screen / bottom-sheet Biometric Muscle Navigator for quickly adding exercises
 * to today's Workout Diary by tapping muscles on the human body.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutBodyMapSheet(
    visible: Boolean,
    exercises: List<ExerciseItem>,
    addedExerciseIds: Set<String>,
    workoutState: WorkoutPersistedState,
    defaultGender: Gender,
    onToggleExercise: (ExerciseItem) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedSlugs by remember { mutableStateOf(setOf("chest")) }

    val targetCatalog = remember(selectedSlugs) { catalogMusclesFromSlugs(selectedSlugs) }
    val matchedExercises = remember(exercises, targetCatalog) {
        val list = if (targetCatalog.isEmpty()) {
            exercises
        } else {
            exercises.filter { ex ->
                ex.primaryMuscles.any { it.lowercase() in targetCatalog } ||
                    ex.secondaryMuscles.any { it.lowercase() in targetCatalog }
            }
        }
        list.sortedByDescending { ex -> ex.primaryMuscles.any { it.lowercase() in targetCatalog } }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF131724),
        contentColor = Color.White
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 760.dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(APEXVectorIcons.Target, contentDescription = null, tint = Color(0xFFFF2E2E), modifier = Modifier.size(20.dp))
                        Text("APEX Biometric Muscle Map", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
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
            }

            item {
                BiometricBodyMapPanel(
                    selectedSlugs = selectedSlugs,
                    onSelectedSlugsChange = { selectedSlugs = it },
                    workoutState = workoutState,
                    defaultGender = defaultGender,
                    matchedCount = matchedExercises.size
                )
            }

            items(matchedExercises.take(45), key = { it.id }) { ex ->
                val isAdded = ex.id in addedExerciseIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(16.dp))
                        .clickable { onToggleExercise(ex) }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(ex.name, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = "${ex.primaryMuscles.joinToString(", ").replaceFirstChar { it.uppercase() }} • ${ex.equipment.replaceFirstChar { it.uppercase() }}",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isAdded) Brush.horizontalGradient(listOf(Color(0x28FFFFFF), Color(0x1EFFFFFF)))
                                else Brush.horizontalGradient(listOf(Color(0xFFE10600), Color(0xFFFF2E2E)))
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isAdded) Icons.Filled.Check else Icons.Filled.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(if (isAdded) "Added" else "Add", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun SegmentedPillBar(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xB8090C14))
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

@Composable
private fun SegmentedPillItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(
                if (selected) Brush.horizontalGradient(listOf(Color(0xFFE10600), Color(0xFFFF2E2E)))
                else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (selected) Color.White else Color.White.copy(alpha = 0.65f), modifier = Modifier.size(12.dp))
        }
        Text(
            text = label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.68f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SplitQuickChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.055f))
            .border(1.dp, Color.White.copy(alpha = 0.11f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Text(label, color = Color.White.copy(alpha = 0.86f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, color = Color.White.copy(alpha = 0.62f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HeatmapKpiTile(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(Color(0xAA0D111B))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(13.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(value, color = valueColor, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text(label.uppercase(), color = Color.White.copy(alpha = 0.50f), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
    }
}
