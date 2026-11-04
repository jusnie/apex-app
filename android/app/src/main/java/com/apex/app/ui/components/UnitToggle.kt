package com.apex.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apex.app.ui.theme.AppColors

/**
 * APEX segmented control: Obsidian Black capsule track with smooth animated
 * Crimson Red active segment and Crisp White typography.
 */
@Composable
fun UnitToggle(
    leftLabel: String,
    rightLabel: String,
    isLeft: Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF181D2B).copy(alpha = 0.78f))
            .border(0.8.dp, Color.White.copy(alpha = 0.16f), shape)
            .padding(4.dp)
    ) {
        Row {
            UnitSegment(
                label = leftLabel,
                selected = isLeft,
                onClick = { if (!isLeft) onSelect(true) },
                modifier = Modifier.weight(1f)
            )
            UnitSegment(
                label = rightLabel,
                selected = !isLeft,
                onClick = { if (isLeft) onSelect(false) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun UnitSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (selected) AppColors.Calorie else Color.Transparent,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "segBg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.68f),
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "segFg"
    )
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .apexPressScale(interactionSource = interactionSource)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = fg,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}
