package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.theme.BrandGold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PremiumSplashScreen(
    onAnimationComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 1. Animation State Holders
    val logoScale = remember { Animatable(0f) }
    val logoAlpha = remember { Animatable(0f) }
    val shineOffset = remember { Animatable(-1.5f) } // normalized -1.5f to 1.5f
    val capScale = remember { Animatable(0f) }
    val capAlpha = remember { Animatable(0f) }
    val capRotation = remember { Animatable(-35f) }
    val arrowProgress = remember { Animatable(0f) }
    val arrowAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textSlideY = remember { Animatable(30f) }
    val subtitleAlpha = remember { Animatable(0f) }
    val subtitleSlideY = remember { Animatable(20f) }
    val bgGlowScale = remember { Animatable(0.9f) }

    // 2. Orchestrate Timeline
    LaunchedEffect(Unit) {
        // Background slow pulse
        launch {
            while (true) {
                bgGlowScale.animateTo(1.2f, animationSpec = tween(2200, easing = EaseInOutSine))
                bgGlowScale.animateTo(0.9f, animationSpec = tween(2200, easing = EaseInOutSine))
            }
        }

        // Logo pop and fade in
        launch {
            logoAlpha.animateTo(1f, animationSpec = tween(1100, easing = EaseOutCubic))
        }
        launch {
            logoScale.animateTo(1f, animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ))
        }

        // Metallic golden shine sweep
        launch {
            delay(1200)
            shineOffset.animateTo(1.5f, animationSpec = tween(1600, easing = EaseInOutQuad))
        }

        // Graduation Cap Anim
        launch {
            delay(1600)
            launch {
                capAlpha.animateTo(1f, animationSpec = tween(600))
            }
            launch {
                capScale.animateTo(1f, animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ))
            }
            launch {
                capRotation.animateTo(0f, animationSpec = spring(
                    dampingRatio = 0.6f,
                    stiffness = Spring.StiffnessLow
                ))
            }
        }

        // Arrow Tracing Anim
        launch {
            delay(2200)
            launch {
                arrowAlpha.animateTo(1f, animationSpec = tween(500))
            }
            launch {
                arrowProgress.animateTo(1f, animationSpec = tween(1200, easing = EaseOutQuart))
            }
        }

        // Main Brand Title Slide Up & Fade In
        launch {
            delay(3200)
            launch {
                textAlpha.animateTo(1f, animationSpec = tween(900, easing = EaseOutCubic))
            }
            launch {
                textSlideY.animateTo(0f, animationSpec = tween(900, easing = EaseOutCubic))
            }
        }

        // Subtitle Fade In
        launch {
            delay(3700)
            launch {
                subtitleAlpha.animateTo(1f, animationSpec = tween(800, easing = EaseOutCubic))
            }
            launch {
                subtitleSlideY.animateTo(0f, animationSpec = tween(800, easing = EaseOutCubic))
            }
        }

        // Finish and Route to Home Screen
        delay(4800)
        onAnimationComplete()
    }

    // 3. Cinematic Canvas UI
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Red and Gold lighting background flares
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(bgGlowScale.value)
        ) {
            // Dark Gold radial flare at center-top
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerOffset = Offset(size.width / 2, size.height * 0.45f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFD700).copy(alpha = 0.16f), // Gold
                            Color(0xFF8B0000).copy(alpha = 0.08f), // Deep Red
                            Color.Transparent
                        ),
                        center = centerOffset,
                        radius = size.width * 0.75f
                    ),
                    radius = size.width * 0.75f,
                    center = centerOffset
                )
                // Red glowing accent flare bottom-right
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF0000).copy(alpha = 0.1f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.7f, size.height * 0.6f),
                        radius = size.width * 0.5f
                    ),
                    radius = size.width * 0.5f,
                    center = Offset(size.width * 0.7f, size.height * 0.6f)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Main Logo Stack (Existing LA monogram logo with custom shine, animated cap and arrow)
            Box(
                modifier = Modifier
                    .size(190.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background gold glowing aura
                Box(
                    modifier = Modifier
                        .size(145.dp)
                        .scale(logoScale.value * 1.05f)
                        .alpha(logoAlpha.value * 0.35f)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFFFBBF24), Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                )

                // 3a. Core Logo with Golden Shine Overlay
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(logoScale.value)
                        .alpha(logoAlpha.value)
                        .border(
                            width = 3.dp,
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color(0xFFFFD700), // Gold
                                    Color(0xFFDC2626), // Red
                                    Color(0xFFFFD700)
                                )
                            ),
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = R.drawable.lakshya_logo,
                        contentDescription = "Lakshya Academy Logo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Moving Golden Shine Overlay
                    val density = LocalDensity.current
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        
                        // Diagonal Golden-White shine gradient
                        val progressX = shineOffset.value * width
                        val shineWidth = width * 0.45f
                        
                        val shineBrush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFFFFD700).copy(alpha = 0.1f),
                                Color.White.copy(alpha = 0.75f),
                                Color(0xFFFFD700).copy(alpha = 0.25f),
                                Color.Transparent
                            ),
                            start = Offset(progressX - shineWidth, 0f),
                            end = Offset(progressX, height)
                        )
                        
                        drawRect(
                            brush = shineBrush,
                            size = Size(width, height),
                            blendMode = BlendMode.SrcAtop
                        )
                    }
                }

                // 3b. Animated Custom-Drawn Glowing Graduation Cap Floating Above
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = (-4).dp, y = (-12).dp)
                        .scale(capScale.value)
                        .rotate(capRotation.value)
                        .alpha(capAlpha.value)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // Draw Graduation Cap Cap Diamond (Gold with fine dark gold borders)
                        val capDiamond = Path().apply {
                            moveTo(w * 0.5f, h * 0.15f) // Top corner
                            lineTo(w * 0.95f, h * 0.38f) // Right corner
                            lineTo(w * 0.5f, h * 0.62f) // Bottom corner
                            lineTo(w * 0.05f, h * 0.38f) // Left corner
                            close()
                        }
                        
                        // Draw Cap Skull Underneath
                        val capBase = Path().apply {
                            moveTo(w * 0.28f, h * 0.49f)
                            quadraticTo(w * 0.5f, h * 0.65f, w * 0.72f, h * 0.49f)
                            lineTo(w * 0.72f, h * 0.68f)
                            quadraticTo(w * 0.5f, h * 0.82f, w * 0.28f, h * 0.68f)
                            close()
                        }

                        // Draw Tassel hanging off the right-bottom edge
                        val tasselPath = Path().apply {
                            moveTo(w * 0.5f, h * 0.38f) // cap center
                            lineTo(w * 0.15f, h * 0.46f) // hanging to the left edge
                            lineTo(w * 0.15f, h * 0.75f) // hanging down
                        }

                        // Glow layer behind the cap
                        drawPath(
                            path = capDiamond,
                            color = Color(0xFFFFD700).copy(alpha = 0.3f),
                            style = Stroke(width = 8f, cap = StrokeCap.Round)
                        )

                        // Filled solid cap parts
                        drawPath(
                            path = capBase,
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF8B0000), Color(0xFFFFD700))
                            )
                        )
                        drawPath(
                            path = capDiamond,
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFFFD700), Color(0xFFD97706), Color(0xFFFFE082))
                            )
                        )
                        
                        // Golden-red borders and tassel
                        drawPath(
                            path = capDiamond,
                            color = Color(0xFF78350F),
                            style = Stroke(width = 3f)
                        )
                        drawPath(
                            path = tasselPath,
                            color = Color(0xFFDC2626),
                            style = Stroke(width = 4f, cap = StrokeCap.Round)
                        )
                        // Tassel end circle
                        drawCircle(
                            color = Color(0xFFFFD700),
                            radius = 4f,
                            center = Offset(w * 0.15f, h * 0.75f)
                        )
                    }
                }

                // 3c. Animated Golden & Red Shooting Arrow swooping from bottom-left to top-right
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(arrowAlpha.value)
                ) {
                    val w = size.width
                    val h = size.height

                    // Curving orbit path representing growth and direction
                    val orbitPath = Path().apply {
                        moveTo(w * 0.05f, h * 0.85f)
                        cubicTo(
                            w * 0.15f, h * 1.05f,
                            w * 0.85f, h * 1.05f,
                            w * 0.95f, h * 0.45f
                        )
                    }

                    // Extract partial segment based on progress
                    val androidPath = orbitPath.asAndroidPath()
                    val pathMeasure = android.graphics.PathMeasure(androidPath, false)
                    val partialAndroidPath = android.graphics.Path()
                    pathMeasure.getSegment(0f, pathMeasure.length * arrowProgress.value, partialAndroidPath, true)
                    val partialComposePath = partialAndroidPath.asComposePath()

                    // Draw the swooping line with a glowing golden-red brush
                    drawPath(
                        path = partialComposePath,
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFFDC2626), Color(0xFFFFD700))
                        ),
                        style = Stroke(width = 5.6f, cap = StrokeCap.Round)
                    )

                    // Draw Arrow Head pointing dynamically
                    if (arrowProgress.value > 0.02f) {
                        val pos = FloatArray(2)
                        val tan = FloatArray(2)
                        pathMeasure.getPosTan(pathMeasure.length * arrowProgress.value, pos, tan)
                        
                        val headX = pos[0]
                        val headY = pos[1]
                        
                        val angle = Math.atan2(tan[1].toDouble(), tan[0].toDouble())
                        
                        // Arrow head vectors relative to tangent angle
                        val headLength = 16f
                        val headAngle = Math.PI / 6 // 30 degrees
                        
                        val leftWingX = headX - headLength * Math.cos(angle - headAngle)
                        val leftWingY = headY - headLength * Math.sin(angle - headAngle)
                        
                        val rightWingX = headX - headLength * Math.cos(angle + headAngle)
                        val rightWingY = headY - headLength * Math.sin(angle + headAngle)

                        val arrowheadPath = Path().apply {
                            moveTo(headX, headY)
                            lineTo(leftWingX.toFloat(), leftWingY.toFloat())
                            lineTo(rightWingX.toFloat(), rightWingY.toFloat())
                            close()
                        }

                        // Fill arrowhead with bright gold
                        drawPath(
                            path = arrowheadPath,
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFFFE082), Color(0xFFFFD700)),
                                center = Offset(headX, headY),
                                radius = 10f
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 4. Lakshya Academy Title
            Text(
                text = "LAKSHYA ACADEMY",
                fontSize = 29.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(y = textSlideY.value.dp)
                    .alpha(textAlpha.value),
                letterSpacing = 1.6.sp
            )

            // Golden-red glowing thin rule line
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(130.dp)
                    .height(2.dp)
                    .alpha(subtitleAlpha.value)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFFFFD700),
                                Color(0xFFDC2626),
                                Color(0xFFFFD700),
                                Color.Transparent
                            )
                        )
                    )
            )

            // 5. Ghazipur Subtitle
            Text(
                text = "GHAZIPUR",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFFD700), // Gold Honors Accent
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(y = subtitleSlideY.value.dp)
                    .alpha(subtitleAlpha.value),
                letterSpacing = 8.sp
            )
        }
    }
}
