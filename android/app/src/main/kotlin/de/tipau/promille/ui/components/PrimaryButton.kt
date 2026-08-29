package de.tipau.promille.ui.components
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors

/**
 * 1:1 mirror of PrimaryButton.swift in iOS.
 * Full-width accent button with 20.dp rounded corners for primary actions.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isDestructive: Boolean = false,
    enabled: Boolean = true
) {
    val baseColor = if (isDestructive) AppColors.statusRed else AppColors.accent

    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = baseColor,
            contentColor = AppColors.background,
            disabledContainerColor = baseColor.copy(alpha = 0.4f),
            disabledContentColor = AppColors.background.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(vertical = 15.dp, horizontal = 20.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(18.dp).padding(end = 8.dp))
            }
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        }
    }
}

/**
 * 1:1 mirror of FABButton.swift in iOS.
 * Floating capsule action button.
 */
@Composable
fun PromilleFAB(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String = "+"
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.accent,
            contentColor = AppColors.background
        ),
        shape = CircleShape, // Capsule
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
        modifier = modifier.height(52.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(icon, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.width(8.dp))
            Text(text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
}
