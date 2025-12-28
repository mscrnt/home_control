package com.homecontrol.sensors.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    // Extra small - for small chips, badges
    extraSmall = RoundedCornerShape(4.dp),

    // Small - for buttons, text fields
    small = RoundedCornerShape(8.dp),

    // Medium - for cards, dialogs
    medium = RoundedCornerShape(12.dp),

    // Large - for bottom sheets, large cards
    large = RoundedCornerShape(16.dp),

    // Extra large - for modal sheets
    extraLarge = RoundedCornerShape(24.dp)
)

// Custom shapes for specific use cases
object HomeControlShapes {
    val Card = RoundedCornerShape(16.dp)
    val Button = RoundedCornerShape(12.dp)
    val BottomSheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val Chip = RoundedCornerShape(8.dp)
    val Dialog = RoundedCornerShape(24.dp)
    val Slider = RoundedCornerShape(4.dp)
    val ProgressBar = RoundedCornerShape(4.dp)
}
