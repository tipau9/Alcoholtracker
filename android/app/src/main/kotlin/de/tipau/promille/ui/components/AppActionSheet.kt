package de.tipau.promille.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import de.tipau.promille.AppColors
import de.tipau.promille.AppText
import io.github.alexzhirkevich.cupertino.AlertDialogActionsScope
import io.github.alexzhirkevich.cupertino.CupertinoActionSheet
import io.github.alexzhirkevich.cupertino.ExperimentalCupertinoApi
import io.github.alexzhirkevich.cupertino.cancel
import io.github.alexzhirkevich.cupertino.default
import io.github.alexzhirkevich.cupertino.destructive

data class ActionSheetItem(
    val title: String,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * 1:1 mirror of iOS UIAlertController(preferredStyle: .actionSheet).
 * Presents grouped options floating above a distinct cancel button with Cupertino animations.
 */
@OptIn(ExperimentalCupertinoApi::class)
@Composable
fun AppActionSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String? = null,
    message: String? = null,
    cancelText: String = "Abbrechen",
    actions: List<ActionSheetItem>
) {
    val haptics = rememberHapticManager()
    CupertinoActionSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
        title = title?.let {
            {
                Text(
                    text = it,
                    style = AppText.captionBold,
                    color = AppColors.textDim
                )
            }
        },
        message = message?.let {
            {
                Text(
                    text = it,
                    style = AppText.caption,
                    color = AppColors.textMuted
                )
            }
        },
        containerColor = AppColors.card,
        buttons = {
            actions.forEach { action ->
                if (action.isDestructive) {
                    destructive(
                        onClick = {
                            haptics.warning()
                            action.onClick()
                            onDismissRequest()
                        }
                    ) {
                        Text(action.title, color = AppColors.statusRed, style = AppText.bodyBold)
                    }
                } else {
                    default(
                        onClick = {
                            haptics.selection()
                            action.onClick()
                            onDismissRequest()
                        }
                    ) {
                        Text(action.title, color = AppColors.accent, style = AppText.body)
                    }
                }
            }
            cancel(
                onClick = {
                    haptics.light()
                    onDismissRequest()
                }
            ) {
                Text(cancelText, style = AppText.bodyBold, color = AppColors.accent)
            }
        }
    )
}
