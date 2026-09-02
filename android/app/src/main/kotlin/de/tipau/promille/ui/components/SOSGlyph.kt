package de.tipau.promille.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/**
 * Stand-in for iOS's `Image(systemName: "sos")`, which renders as the literal
 * letters "SOS" rather than a pictogram — there is no vector shape to port,
 * so this renders the word itself instead of a generic warning triangle.
 */
@Composable
fun SOSGlyph(tint: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Text(
            text = "SOS",
            color = tint,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
