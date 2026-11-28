package com.example.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import kotlin.math.abs

enum class BunnyCompanionExpression {
    NEUTRAL,
    TALKING,
    LISTENING,
    HAPPY,
    SAD,
    PATTED,
    CHEEK_PINCHED
}

/**
 * Interactive animated Forget-Me-Not bunny companion supporting live expressions
 * (happy celebration, sad droopy ears, cheerful talking, blissful head-pat squish,
 * and squishy cheek pinching) rendered with studio artwork and spring physics.
 */
@Composable
fun BunnyCompanionAvatar(
    breed: BunnyBreed,
    expression: BunnyCompanionExpression,
    isTalking: Boolean,
    patPulse: Float,
    leftCheekPull: Float,
    rightCheekPull: Float,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    contentDesc: String? = null
) {
    val reducedMotion = rememberReducedMotion()

    val idleBreathState = if (reducedMotion) {
        remember { mutableFloatStateOf(0f) }
    } else {
        val breathTransition = rememberInfiniteTransition(label = "month_bunny_breath")
        breathTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = if (expression == BunnyCompanionExpression.SAD) 2800 else 2000,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bunny_breath"
        )
    }

    val talkCycleState = if (reducedMotion || !isTalking) {
        remember { mutableFloatStateOf(0f) }
    } else {
        val talkTransition = rememberInfiniteTransition(label = "month_bunny_talk")
        talkTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 160, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bunny_talk"
        )
    }

    val animatedPat by animateFloatAsState(
        targetValue = patPulse.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "bunny_pat_spring"
    )
    val animatedCheek by animateFloatAsState(
        targetValue = (abs(leftCheekPull) + abs(rightCheekPull)).coerceIn(0f, 1.5f),
        animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow),
        label = "bunny_cheek_spring"
    )

    val drawableRes = when {
        expression == BunnyCompanionExpression.PATTED -> R.drawable.bunny_pose_patted
        expression == BunnyCompanionExpression.CHEEK_PINCHED -> R.drawable.bunny_expr_cheek_pinched
        expression == BunnyCompanionExpression.SAD -> R.drawable.bunny_expr_sad
        expression == BunnyCompanionExpression.HAPPY -> R.drawable.bunny_pose_celebrating
        isTalking || expression == BunnyCompanionExpression.TALKING -> R.drawable.bunny_expr_talking
        else -> R.drawable.bunny_pose_greeting
    }

    val breath = idleBreathState.value
    val talkBounce = talkCycleState.value
    val scaleX = (1f + animatedPat * 0.09f + animatedCheek * 0.11f).coerceIn(0.88f, 1.18f)
    val scaleY = (1f - animatedPat * 0.08f - animatedCheek * 0.06f + breath * 0.025f + talkBounce * 0.045f)
        .coerceIn(0.84f, 1.14f)
    val tiltDeg = ((rightCheekPull - leftCheekPull) * 9f).coerceIn(-11f, 11f)

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (contentDesc != null) Modifier.semantics { contentDescription = contentDesc }
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = drawableRes,
            animationSpec = tween(durationMillis = if (reducedMotion) 0 else 150),
            label = "interactive_bunny_expression"
        ) { resId ->
            Image(
                painter = painterResource(id = resId),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        this.scaleX = scaleX
                        this.scaleY = scaleY
                        this.rotationZ = tiltDeg
                        this.translationY = -talkBounce * 4f - breath * 2f
                    }
            )
        }
    }
}
