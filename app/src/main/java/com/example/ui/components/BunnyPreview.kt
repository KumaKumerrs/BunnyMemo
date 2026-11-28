package com.example.ui.components

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * Standard multi-preview matrix for BunnyMemo components and screens:
 * Light, Dark, Compact 360dp phone, and Large Font (1.5x) accessibility check.
 */
@Preview(name = "1 · Light", showBackground = true, widthDp = 412, heightDp = 820)
@Preview(
    name = "2 · Dark",
    showBackground = true,
    widthDp = 412,
    heightDp = 820,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Preview(name = "3 · Compact 360dp", showBackground = true, widthDp = 360, heightDp = 780)
@Preview(name = "4 · Large Font 1.5x", showBackground = true, widthDp = 360, heightDp = 780, fontScale = 1.5f)
annotation class BunnyPreview
