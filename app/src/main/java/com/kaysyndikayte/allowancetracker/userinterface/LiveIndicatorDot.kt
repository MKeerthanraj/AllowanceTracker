package com.kaysyndikayte.allowancetracker.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LiveIndicatorDot(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "live_dot_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_dot_alpha"
    )

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(10.dp)
            .alpha(alpha)
            .background(Color(0xFF2E7D32), CircleShape)
    )
}