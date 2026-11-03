package com.apex.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.apex.app.ui.theme.AppColors

/**
 * Silky spring tactile press modifier applied across interactive buttons & cards.
 */
fun Modifier.apexPressScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.965f
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "apexPressScale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun APEXGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    padding: Dp = 16.dp,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val baseColor = Color(0xFF0A0A0F).copy(alpha = 0.90f)
    val shadowColor = Color.Black.copy(alpha = 0.52f)
    val sheen = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.06f),
            Color.White.copy(alpha = 0.015f),
            AppColors.Calorie.copy(alpha = 0.032f)
        )
    )
    val border = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = 0.16f),
            AppColors.Calorie.copy(alpha = 0.24f),
            Color.White.copy(alpha = 0.05f)
        )
    )

    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(
            modifier = modifier
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                .shadow(
                    elevation = 14.dp,
                    shape = shape,
                    ambientColor = shadowColor,
                    spotColor = AppColors.Calorie.copy(alpha = 0.18f)
                )
                .clip(shape)
                .background(baseColor)
                .background(sheen)
                .border(0.8.dp, border, shape)
                .padding(padding),
            contentAlignment = contentAlignment,
            content = content
        )
    }
}

@Composable
fun APEXGlassColumn(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    APEXGlassSurface(
        modifier = modifier,
        cornerRadius = cornerRadius,
        padding = 0.dp
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun APEXIconBubble(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    iconSize: Dp = 19.dp,
    tint: Color = AppColors.Calorie
) {
    val plainIconSize = if (iconSize < size * 0.88f) size * 0.88f else iconSize
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(plainIconSize)
        )
    }
}

@Composable
fun APEXGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle = TextStyle(
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
    )
) {
    val shape = RoundedCornerShape(18.dp)
    val fieldFill = Color(0xFF121219).copy(alpha = 0.92f)
    val fieldSheen = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.06f),
            Color.White.copy(alpha = 0.015f),
            AppColors.Calorie.copy(alpha = 0.025f)
        )
    )
    val fieldBorder = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = 0.16f),
            AppColors.Calorie.copy(alpha = 0.22f)
        )
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        textStyle = textStyle.copy(color = Color.White),
        cursorBrush = SolidColor(AppColors.Calorie),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (singleLine) 52.dp else 118.dp)
            .clip(shape)
            .background(fieldFill)
            .background(fieldSheen)
            .border(0.7.dp, fieldBorder, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        decorationBox = { inner ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
            ) {
                if (value.isEmpty() && placeholder.isNotBlank()) {
                    Text(
                        placeholder,
                        color = Color.White.copy(alpha = 0.44f),
                        fontSize = textStyle.fontSize,
                        fontWeight = FontWeight.Medium
                    )
                }
                inner()
            }
        }
    )
}

@Composable
fun APEXGlassDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            APEXGlassSurface(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                cornerRadius = 28.dp,
                padding = 20.dp
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
fun APEXGlassPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    height: Dp = 50.dp,
    content: (@Composable RowScope.() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val brush = if (enabled) {
        Brush.linearGradient(listOf(AppColors.CalorieStart, AppColors.CalorieEnd))
    } else {
        Brush.linearGradient(
            listOf(
                AppColors.CalorieStart.copy(alpha = 0.36f),
                AppColors.CalorieEnd.copy(alpha = 0.36f)
            )
        )
    }
    Row(
        modifier
            .apexPressScale(interactionSource = interactionSource, enabled = enabled)
            .height(height)
            .shadow(
                elevation = if (enabled) 10.dp else 0.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = AppColors.Calorie.copy(alpha = 0.35f),
                spotColor = AppColors.Calorie.copy(alpha = 0.45f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(brush)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            if (content != null) {
                content()
            } else {
                Text(
                    text,
                    color = if (enabled) Color.White else Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun APEXGlassTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    enabled: Boolean = true,
    filledPrimary: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)
    val fillBrush = if (filledPrimary) {
        if (enabled) Brush.linearGradient(listOf(AppColors.CalorieStart, AppColors.CalorieEnd))
        else Brush.linearGradient(listOf(AppColors.CalorieStart.copy(alpha = 0.35f), AppColors.CalorieEnd.copy(alpha = 0.35f)))
    } else {
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.06f)))
    }
    val border = if (filledPrimary) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.14f)
    val textColor = if (filledPrimary) {
        if (enabled) Color.White else Color.White.copy(alpha = 0.55f)
    } else {
        color.copy(alpha = if (enabled) color.alpha else color.alpha * 0.40f)
    }
    Box(
        modifier
            .apexPressScale(interactionSource = interactionSource, enabled = enabled)
            .clip(shape)
            .background(fillBrush)
            .border(0.6.dp, border, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun APEXGlassDialogActions(
    primaryText: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    destructive: Boolean = false,
    primaryEnabled: Boolean = true
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dismissText != null && onDismiss != null) {
            APEXGlassTextButton(
                text = dismissText,
                onClick = onDismiss,
                color = Color.White.copy(alpha = 0.80f),
                filledPrimary = false
            )
            Spacer(Modifier.width(8.dp))
        }
        APEXGlassTextButton(
            text = primaryText,
            onClick = onPrimary,
            color = Color.White,
            enabled = primaryEnabled,
            filledPrimary = true
        )
    }
}
