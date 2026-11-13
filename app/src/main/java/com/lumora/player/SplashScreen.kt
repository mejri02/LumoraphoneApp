package com.lumora.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * The first screen of the app: a dark stage, the animated Lumore mark, the word mark and the
 * "developed by Mejri02" credit. It dismisses itself after the intro and never blocks playback.
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val mark = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val credit = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        mark.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        word.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        credit.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        delay(720)
        onDone()
    }

    // A slow breathing glow behind the mark. It gives the black stage some life without noise.
    val glow = rememberInfiniteTransition(label = "splash-glow")
    val pulse by glow.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "splash-pulse"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF07070B), Color(0xFF020204), Color(0xFF0A0612))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Soft pink/violet halo.
        Box(
            Modifier
                .size(320.dp)
                .graphicsLayer(scaleX = pulse, scaleY = pulse, alpha = 0.55f)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0x55FF2E88), Color(0x338B5CF6), Color.Transparent)
                    ),
                    CircleShape
                )
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // ---- animated logo tile ----
            Box(
                Modifier
                    .size(92.dp)
                    .graphicsLayer(
                        scaleX = 0.72f + 0.28f * mark.value,
                        scaleY = 0.72f + 0.28f * mark.value,
                        alpha = mark.value,
                        rotationZ = -8f + 8f * mark.value
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFFF2E88), Color(0xFF8B5CF6), Color(0xFF22D3EE))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "L",
                    color = Color.White,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(Modifier.height(28.dp))

            // ---- word mark ----
            Text(
                "LUMORE",
                style = TextStyle(
                    brush = Brush.horizontalGradient(
                        listOf(Color.White, Color(0xFFFFD6EA), Color.White)
                    ),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 8.sp
                ),
                modifier = Modifier
                    .graphicsLayer(alpha = word.value, translationY = (1f - word.value) * 16f)
            )

            Spacer(Modifier.height(12.dp))

            // Thin gradient rule between the name and the credit.
            Box(
                Modifier
                    .width(74.dp)
                    .height(2.dp)
                    .graphicsLayer(alpha = credit.value)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color(0xFFFF2E88), Color(0xFF8B5CF6), Color.Transparent)
                        )
                    )
            )

            Spacer(Modifier.height(14.dp))

            Text(
                "DEVELOPED BY MEJRI02",
                color = Color(0xFF9AA0AE),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer(
                    alpha = credit.value,
                    translationY = (1f - credit.value) * 10f
                )
            )
        }

        // Bottom caption + a quiet loading shimmer.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 54.dp)
                .graphicsLayer(alpha = credit.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "IPTV PLAYER",
                color = Color(0xFF565B68),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 4.sp
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i ->
                    Box(
                        Modifier
                            .size(5.dp)
                            .graphicsLayer(alpha = 0.25f + 0.55f * ((i + 1) / 3f) * credit.value)
                            .background(Color(0xFFFF2E88), CircleShape)
                    )
                }
            }
        }
    }
}
