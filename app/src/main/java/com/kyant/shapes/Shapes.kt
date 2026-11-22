package com.kyant.shapes

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

@Immutable
data class CornerRadii(
    val topLeft: Float,
    val topRight: Float,
    val bottomRight: Float,
    val bottomLeft: Float
)

interface RoundedRectangularShape : Shape {
    fun corners(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): CornerRadii
}

@Immutable
class Capsule : RoundedRectangularShape {
    override fun corners(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): CornerRadii {
        val radius = size.minDimension / 2f
        return CornerRadii(
            topLeft = radius,
            topRight = radius,
            bottomRight = radius,
            bottomLeft = radius
        )
    }

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val radius = size.minDimension / 2f
        return Outline.Rounded(RoundRect(size.toRect(), CornerRadius(radius, radius)))
    }

    override fun equals(other: Any?): Boolean = other is Capsule

    override fun hashCode(): Int = javaClass.hashCode()
}
