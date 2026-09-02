package de.tipau.promille.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.tipau.promille.AppColors
import de.tipau.promille.AppText

/**
 * 1:1 mirror of custom themed alert dialogs in iOS.
 * 20dp rounded corners, card background, 0.5dp border, formatted actions.
 */
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: String? = null,
    confirmText: String = "OK",
    onConfirm: () -> Unit,
    dismissText: String? = "Abbrechen",
    isDestructive: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.card, RoundedCornerShape(20.dp))
                    .border(0.5.dp, AppColors.border, RoundedCornerShape(20.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = title,
                    style = AppText.bodyBold,
                    color = AppColors.text
                )
                if (text != null) {
                    Text(
                        text = text,
                        style = AppText.caption,
                        color = AppColors.textDim
                    )
                }
                if (content != null) {
                    content()
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (dismissText != null) {
                        SecondaryButton(
                            text = dismissText,
                            onClick = onDismissRequest,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    PrimaryButton(
                        text = confirmText,
                        onClick = {
                            onConfirm()
                            onDismissRequest()
                        },
                        isDestructive = isDestructive,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
