package com.fox.music.core.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.fox.music.core.ui.R

actual val FoxDisplayFont = FontFamily(
    Font(R.font.fraunces, FontWeight.Normal),
    Font(R.font.fraunces, FontWeight.Medium),
    Font(R.font.fraunces, FontWeight.SemiBold),
    Font(R.font.fraunces, FontWeight.Bold),
)

actual val FoxBodyFont = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_regular, FontWeight.Medium),
    Font(R.font.manrope_regular, FontWeight.SemiBold),
    Font(R.font.manrope_regular, FontWeight.Bold),
)
