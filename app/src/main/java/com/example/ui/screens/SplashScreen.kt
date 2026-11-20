package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.OfficialAhaLogo
import com.example.ui.theme.BrandDarkRed
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandOrange
import com.example.ui.theme.BrandRed
import com.example.ui.theme.BrandWhite
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val scale = remember { Animatable(0.72f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(1f, animationSpec = tween(550, easing = FastOutSlowInEasing))
        alpha.animateTo(1f, animationSpec = tween(450))
        delay(850)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BrandWhite, BrandWhite, BrandRed.copy(alpha = 0.08f))
                )
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .scale(scale.value)
                .alpha(alpha.value.coerceAtLeast(0.4f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                shadowElevation = 14.dp,
                color = BrandWhite,
                modifier = Modifier.size(164.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                    OfficialAhaLogo(size = 148.dp)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "AhaPDF",
                style = MaterialTheme.typography.displayLarge.copy(
                    color = BrandNavy,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Scan • Convert • Protect • Share",
                style = MaterialTheme.typography.bodyLarge,
                color = BrandDarkRed,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(28.dp))
            LinearProgressIndicator(
                color = BrandRed,
                trackColor = BrandOrange.copy(alpha = 0.25f),
                modifier = Modifier.size(width = 160.dp, height = 5.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "DEVELOPED BY",
                style = MaterialTheme.typography.labelSmall,
                color = BrandNavy.copy(alpha = 0.7f),
                letterSpacing = 1.6.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "AHANA ENTERPRISE",
                style = MaterialTheme.typography.titleMedium,
                color = BrandNavy,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }
    }
}
