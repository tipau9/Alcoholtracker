package de.tipau.promille.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.tipau.promille.AppColors
import de.tipau.promille.AppText

/**
 * Native Apple iOS Swipe-to-Delete and Swipe-to-Duplicate row with hardware haptic & sound feedback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppleSwipeRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    onDuplicate: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberHapticManager()
    val density = LocalDensity.current

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    haptics.warning()
                    AppAudio.playClick(context)
                    onDelete()
                    false
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (onDuplicate != null) {
                        haptics.success()
                        AppAudio.playClick(context)
                        onDuplicate()
                    }
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { with(density) { 64.dp.toPx() } }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = onDuplicate != null,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val isEndToStart = direction == SwipeToDismissBoxValue.EndToStart
            val isStartToEnd = direction == SwipeToDismissBoxValue.StartToEnd

            val backgroundColor = when {
                isEndToStart -> AppColors.statusRed
                isStartToEnd -> AppColors.statusGreen
                else -> Color.Transparent
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(backgroundColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (isStartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (isEndToStart) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Löschen",
                            color = Color.White,
                            style = AppText.captionBold
                        )
                        Icon(
                            painter = AppIcons.Trash,
                            contentDescription = "Löschen",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else if (isStartToEnd) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = AppIcons.Copy,
                            contentDescription = "Nochmal",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Nochmal (+1)",
                            color = Color.White,
                            style = AppText.captionBold
                        )
                    }
                }
            }
        },
        content = {
            content()
        }
    )
}
