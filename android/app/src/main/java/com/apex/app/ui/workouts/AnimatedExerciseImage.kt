package com.apex.app.ui.workouts

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.imageLoader
import coil.request.ImageRequest
import com.apex.app.data.ExerciseRepository
import com.apex.app.data.ExerciseVisual
import com.apex.app.data.ExerciseVisualFormat
import com.apex.app.models.UserExercise
import com.apex.app.services.FoodImageStore
import com.apex.app.services.WorkoutFrameRef
import com.apex.app.services.WorkoutFrameStore
import java.io.File
import kotlinx.coroutines.delay

private val ExerciseImageFilter: ColorFilter = run {
    val saturation = ColorMatrix().apply { setToSaturation(0.19f) }
    val contrast = 1.10f
    val translate = (1f - contrast) * 127.5f + (-0.05f * 255f)
    val contrastBrightness = ColorMatrix(
        floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
    )
    contrastBrightness.timesAssign(saturation)
    ColorFilter.colorMatrix(contrastBrightness)
}

private fun resolveOfflineExerciseMedia(context: Context, relativePath: String?): File? {
    val rel = relativePath?.trim()?.trimStart('/')?.takeIf { it.isNotEmpty() } ?: return null
    val candidates = buildList {
        add(File(context.filesDir, "exercise-media/$rel"))
        add(File(context.filesDir, rel))
        context.getExternalFilesDir(null)?.let { ext ->
            add(File(ext, "exercise-media/$rel"))
            add(File(ext, rel))
        }
    }
    return candidates.firstOrNull { it.isFile && it.length() > 0L }
}

@Composable
fun AnimatedExerciseImage(
    visual: ExerciseVisual,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackLabel: String? = null,
    animatesFrames: Boolean = true
) {
    val colors = workoutsColors()
    val context = LocalContext.current
    val animationsEnabled = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    val shouldAnimate = animatesFrames && animationsEnabled

    // 1. Primary path: Native MP4 Video + Poster Thumbnail (offline local file first -> R2 URL fallback)
    if (visual.hasVideoMedia) {
        val localThumb = remember(context, visual.localThumbnailPath) {
            resolveOfflineExerciseMedia(context, visual.localThumbnailPath)
        }
        val localVideo = remember(context, visual.localVideoPath) {
            resolveOfflineExerciseMedia(context, visual.localVideoPath)
        }
        val thumbSource: Any? = localThumb ?: visual.thumbnailUrl
        val videoUri: Uri? = remember(localVideo, visual.videoUrl) {
            when {
                localVideo != null -> Uri.fromFile(localVideo)
                !visual.videoUrl.isNullOrBlank() -> Uri.parse(visual.videoUrl)
                else -> null
            }
        }

        var thumbFailed by remember(thumbSource) { mutableStateOf(thumbSource == null) }
        var videoReady by remember(videoUri, shouldAnimate) { mutableStateOf(false) }

        Box(modifier.background(colors.background)) {
            if (thumbFailed && !videoReady) {
                ExerciseImagePlaceholder(Modifier.fillMaxSize(), colors, fallbackLabel)
            }
            if (thumbSource != null) {
                AsyncImage(
                    model = remember(thumbSource) {
                        ImageRequest.Builder(context)
                            .data(thumbSource)
                            .crossfade(true)
                            .build()
                    },
                    contentDescription = fallbackLabel,
                    contentScale = contentScale,
                    onSuccess = { thumbFailed = false },
                    onError = { thumbFailed = true },
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (shouldAnimate && videoUri != null) {
                ExerciseLoopingVideoPlayer(
                    uri = videoUri,
                    onReady = { videoReady = true },
                    onError = { videoReady = false },
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (videoReady) 1f else 0f)
                )
            }
        }
        return
    }

    // 2. Fallback path: User custom photo or authored frame sequence
    val imagePaths = visual.framePaths
    if (imagePaths.isEmpty()) {
        ExerciseImagePlaceholder(modifier, colors, fallbackLabel)
        return
    }

    val imageStore = remember(context) { FoodImageStore(context) }
    val frameStore = remember(context) { WorkoutFrameStore.get(context) }
    val imageLoader = if (visual.isAuthored) frameStore.imageLoader else context.imageLoader
    val representativeIndex = visual.representativeFrameIndex.coerceIn(imagePaths.indices)
    var index by remember(imagePaths, visual.format, shouldAnimate) {
        mutableIntStateOf(if (shouldAnimate) 0 else representativeIndex)
    }

    LaunchedEffect(imagePaths, visual.format, shouldAnimate) {
        if (!shouldAnimate) {
            index = representativeIndex
            return@LaunchedEffect
        }
        if (imagePaths.size <= 1) return@LaunchedEffect
        while (true) {
            delay(850)
            index = (index + 1) % imagePaths.size
        }
    }

    val prefetchIndex = if (shouldAnimate && imagePaths.size > 1) (index + 1) % imagePaths.size else null

    LaunchedEffect(prefetchIndex, visual) {
        val next = prefetchIndex ?: return@LaunchedEffect
        imageLoader.enqueue(exerciseImageRequest(context, imageStore, visual, next))
    }

    val isJpeg = visual.format == ExerciseVisualFormat.JPEG
    var frameUnavailable by remember(visual) { mutableStateOf(false) }
    var retryAttempt by remember(visual) { mutableIntStateOf(0) }
    LaunchedEffect(frameUnavailable, retryAttempt, visual) {
        if (!frameUnavailable || !visual.isAuthored) return@LaunchedEffect
        delay(frameRetryDelayMillis(retryAttempt))
        retryAttempt++
    }
    Box(modifier.background(colors.background)) {
        if (frameUnavailable) {
            ExerciseImagePlaceholder(Modifier.fillMaxSize(), colors, fallbackLabel)
        }
        AsyncImage(
            model = remember(visual, index, retryAttempt) {
                exerciseImageRequest(context, imageStore, visual, index, retryAttempt)
            },
            imageLoader = imageLoader,
            contentDescription = null,
            onSuccess = { frameUnavailable = false },
            onError = { frameUnavailable = true },
            contentScale = if (isJpeg) contentScale else ContentScale.Fit,
            colorFilter = if (isJpeg) ExerciseImageFilter else null,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ExerciseLoopingVideoPlayer(
    uri: Uri,
    onReady: () -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeView by remember { mutableStateOf<VideoView?>(null) }

    DisposableEffect(uri) {
        onDispose {
            runCatching { activeView?.stopPlayback() }
            activeView = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            VideoView(ctx).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE)
                }
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    mp.setVolume(0f, 0f)
                    runCatching {
                        mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                    }
                    start()
                    onReady()
                }
                setOnErrorListener { _, _, _ ->
                    onError()
                    true
                }
                activeView = this
                setVideoURI(uri)
            }
        },
        update = { view ->
            if (activeView !== view) {
                activeView = view
            }
        }
    )
}

@Composable
private fun ExerciseImagePlaceholder(
    modifier: Modifier,
    colors: WorkoutsColors,
    fallbackLabel: String?
) {
    Box(
        modifier.background(
            Brush.linearGradient(listOf(colors.panel, colors.card, colors.accent.copy(alpha = 0.12f)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.FitnessCenter, null, tint = colors.charcoal, modifier = Modifier.size(36.dp))
            if (!fallbackLabel.isNullOrBlank()) {
                Text(
                    fallbackLabel.uppercase(),
                    color = colors.charcoal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }
        }
    }
}

private fun frameRetryDelayMillis(attempt: Int): Long =
    (FRAME_RETRY_BASE_MS shl attempt.coerceIn(0, 2)).coerceAtMost(FRAME_RETRY_MAX_MS)

private const val FRAME_RETRY_BASE_MS = 15_000L
private const val FRAME_RETRY_MAX_MS = 60_000L
private const val FRAME_RETRY_ATTEMPT_PARAMETER = "workoutFrameRetryAttempt"

private fun exerciseImageRequest(
    context: Context,
    imageStore: FoodImageStore,
    visual: ExerciseVisual,
    index: Int,
    retryAttempt: Int = 0
): ImageRequest {
    val path = visual.framePaths[index]
    if (visual.isAuthored) {
        val ref = WorkoutFrameRef.from(path, visual.digestAt(index), visual.format)
        if (ref != null) {
            val cacheKey = "${ref.name}:${ref.digest ?: "nodigest"}"
            return ImageRequest.Builder(context)
                .data(ref)
                .memoryCacheKey(cacheKey)
                .diskCacheKey(cacheKey)
                .setParameter(FRAME_RETRY_ATTEMPT_PARAMETER, retryAttempt, memoryCacheKey = null)
                .build()
        }
    }
    val localFile = path
        .takeIf { UserExercise.isUserPhotoFilename(it) }
        ?.let { imageStore.file(it).takeIf { file -> file.isFile } }
    val builder = ImageRequest.Builder(context)
        .data(localFile ?: ExerciseRepository.imageAssetUri(path))
        .memoryCacheKey(path)
        .diskCacheKey(path)
    if (visual.format == ExerciseVisualFormat.SVG) {
        builder.decoderFactory(SvgDecoder.Factory())
    }
    return builder.build()
}
