package app.gamenative.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.TransformOrigin
import app.gamenative.PrefManager

object PluviaAnimation {
    /**
     * The "Jiggly" bubbly spring spec.
     * DampingRatio = 0.4f gives a more aggressive bounce.
     * StiffnessMediumLow makes it feel snappier but still organic.
     */
    val BubblySpringSpec = spring<Float>(
        dampingRatio = 0.45f,
        stiffness = 400f, // Custom stiffness for better responsiveness
    )

    /**
     * Transition for pages opening from the left (like a sidebar).
     */
    fun slideInFromLeft(): EnterTransition {
        if (!PrefManager.jigglyAnimations) return fadeIn(animationSpec = tween(300))

        return slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.5f)
        ) + scaleIn(
            initialScale = 0.88f,
            transformOrigin = TransformOrigin(0f, 0.5f),
            animationSpec = BubblySpringSpec
        ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
    }

    fun slideOutToLeft(): ExitTransition {
        if (!PrefManager.jigglyAnimations) return fadeOut(animationSpec = tween(250))

        return slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = spring(stiffness = Spring.StiffnessMedium)
        ) + scaleOut(
            targetScale = 0.88f,
            transformOrigin = TransformOrigin(0f, 0.5f),
            animationSpec = spring(stiffness = Spring.StiffnessMedium)
        ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium))
    }

    /**
     * Transition for screens opening (like Settings).
     */
    fun enterFromRight(): EnterTransition {
        if (!PrefManager.jigglyAnimations) return fadeIn(animationSpec = tween(300))

        return slideInHorizontally(
            initialOffsetX = { it / 2 }, // Half slide for smoother feel
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.5f)
        ) + scaleIn(
            initialScale = 0.92f,
            transformOrigin = TransformOrigin(0.5f, 0.5f),
            animationSpec = BubblySpringSpec
        ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
    }

    fun exitToRight(): ExitTransition {
        if (!PrefManager.jigglyAnimations) return fadeOut(animationSpec = tween(250))

        return slideOutHorizontally(
            targetOffsetX = { it / 2 },
            animationSpec = spring(stiffness = Spring.StiffnessMedium)
        ) + scaleOut(
            targetScale = 0.92f,
            transformOrigin = TransformOrigin(0.5f, 0.5f),
            animationSpec = spring(stiffness = Spring.StiffnessMedium)
        ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium))
    }
}
