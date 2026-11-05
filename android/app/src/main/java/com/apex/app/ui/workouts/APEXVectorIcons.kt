package com.apex.app.ui.workouts

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Modern 24x24 Lucide vector icons for APEX Fitness Gym workout features.
 * Zero emojis — 100% crisp geometric vector strokes.
 */
object APEXVectorIcons {

    private fun lucideStrokeIcon(name: String, pathDataList: List<String>, strokeWidth: Float = 2.1f): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        for (d in pathDataList) {
            val nodes = PathParser().parsePathString(d).toNodes()
            builder.addPath(
                pathData = nodes,
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return builder.build()
    }

    val Trophy: ImageVector by lazy {
        lucideStrokeIcon(
            "LucideTrophy",
            listOf(
                "M6 9H4.5a2.5 2.5 0 0 1 0-5H6",
                "M18 9h1.5a2.5 2.5 0 0 0 0-5H18",
                "M4 22h16",
                "M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22",
                "M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22",
                "M18 2H6v7a6 6 0 0 0 12 0V2Z"
            )
        )
    }

    val Award: ImageVector by lazy {
        lucideStrokeIcon(
            "LucideAward",
            listOf(
                "M15.477 12.89 17 22l-5-3-5 3 1.523-9.11",
                "M12 2a6 6 0 1 0 0 12 6 6 0 1 0 0-12Z"
            )
        )
    }

    val Flame: ImageVector by lazy {
        lucideStrokeIcon(
            "LucideFlame",
            listOf(
                "M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294 1-3a2.5 2.5 0 0 0 2.5 2.5z"
            )
        )
    }

    val Dumbbell: ImageVector by lazy {
        lucideStrokeIcon(
            "LucideDumbbell",
            listOf(
                "M14.4 14.4 9.6 9.6",
                "M18.657 21.485a2 2 0 1 1-2.829-2.828l-1.767 1.768a2 2 0 1 1-2.829-2.829l6.364-6.364a2 2 0 1 1 2.829 2.829l-1.768 1.767a2 2 0 1 1 2.828 2.829z",
                "m21.5 21.5-1.4-1.4",
                "M3.9 3.9 2.5 2.5",
                "M6.404 12.768a2 2 0 1 1-2.829-2.829l1.768-1.767a2 2 0 1 1-2.828-2.829l2.828-2.828a2 2 0 1 1 2.829 2.828l1.767-1.768a2 2 0 1 1 2.829 2.829z"
            )
        )
    }

    val Timer: ImageVector by lazy {
        lucideStrokeIcon(
            "LucideTimer",
            listOf(
                "M10 2h4",
                "M12 14l3-3",
                "M12 6a8 8 0 1 0 0 16 8 8 0 1 0 0-16Z"
            )
        )
    }

    val Layers: ImageVector by lazy {
        lucideStrokeIcon(
            "LucideLayers",
            listOf(
                "m12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83Z",
                "m22 17.65-9.17 4.16a2 2 0 0 1-1.66 0L2 17.65",
                "m22 12.65-9.17 4.16a2 2 0 0 1-1.66 0L2 12.65"
            )
        )
    }

    val Zap: ImageVector by lazy {
        lucideStrokeIcon(
            "LucideZap",
            listOf("M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z")
        )
    }

    val Target: ImageVector by lazy {
        lucideStrokeIcon(
            "LucideTarget",
            listOf(
                "M12 2a10 10 0 1 0 0 20 10 10 0 1 0 0-20Z",
                "M12 6a6 6 0 1 0 0 12 6 6 0 1 0 0-12Z",
                "M12 10a2 2 0 1 0 0 4 2 2 0 1 0 0-4Z"
            )
        )
    }

    val Share2: ImageVector by lazy {
        lucideStrokeIcon(
            "LucideShare2",
            listOf(
                "M18 2a3 3 0 1 0 0 6 3 3 0 1 0 0-6Z",
                "M6 9a3 3 0 1 0 0 6 3 3 0 1 0 0-6Z",
                "M18 16a3 3 0 1 0 0 6 3 3 0 1 0 0-6Z",
                "m8.59 13.51 6.83 3.98",
                "m15.41 6.51-6.82 3.98"
            )
        )
    }

    val Download: ImageVector by lazy {
        lucideStrokeIcon(
            "LucideDownload",
            listOf(
                "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4",
                "m7 10 5 5 5-5",
                "M12 15V3"
            )
        )
    }

    val ImageOverlay: ImageVector by lazy {
        lucideStrokeIcon(
            "LucideImage",
            listOf(
                "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z",
                "M9 7a2 2 0 1 0 0 4 2 2 0 1 0 0-4Z",
                "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"
            )
        )
    }

    val RotateCcw: ImageVector by lazy {
        lucideStrokeIcon(
            "LucideRotateCcw",
            listOf(
                "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8",
                "M3 3v5h5"
            )
        )
    }

    val ShieldCheck: ImageVector by lazy {
        lucideStrokeIcon(
            "LucideShieldCheck",
            listOf(
                "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
                "m9 12 2 2 4-4"
            )
        )
    }
}
