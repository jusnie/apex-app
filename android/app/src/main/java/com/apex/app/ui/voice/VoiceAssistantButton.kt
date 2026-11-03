package com.apex.app.ui.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.apex.app.R
import com.apex.app.services.voice.OrbMode
import com.apex.app.ui.theme.AppColors

/** The shared floating tactical reactor entry point on Home and the workout diary. */
@Composable
fun VoiceAssistantButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 60.dp) {
    val label = stringResource(R.string.voice_talk_to_apex)
    Box(
        modifier
            .size(size)
            .shadow(elevation = 10.dp, shape = CircleShape, ambientColor = AppColors.Calorie, spotColor = AppColors.Calorie)
            .clip(CircleShape)
            .background(Color(0xE008080D))
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    listOf(
                        AppColors.Calorie.copy(alpha = 0.68f),
                        Color.White.copy(alpha = 0.24f),
                        AppColors.Calorie.copy(alpha = 0.30f)
                    )
                ),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .testTag("voiceAssistantButton")
    ) {
        LiveOrb(
            mode = OrbMode.IDLE,
            tint = AppColors.Calorie,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = 1f / 0.78f
                scaleY = 1f / 0.78f
            },
            clearBackground = true
        )
    }
}
