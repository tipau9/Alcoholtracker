package de.tipau.promille.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.commandiron.wheel_picker_compose.WheelTimePicker
import com.commandiron.wheel_picker_compose.core.TimeFormat
import de.tipau.promille.AppColors
import java.time.LocalTime

/**
 * iOS UIDatePicker "wheels" style replaces Android's system TimePickerDialog,
 * which pops a Material clock face with none of the app's own styling.
 */
@Composable
fun WheelTimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit
) {
    var snapped by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(AppColors.card, RoundedCornerShape(20.dp))
                .padding(20.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            WheelTimePicker(
                startTime = initial,
                timeFormat = TimeFormat.HOUR_24,
                size = DpSize(220.dp, 160.dp),
                textColor = AppColors.text,
                onSnappedTime = { snapped = it }
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SecondaryButton(text = "Abbrechen", onClick = onDismiss, modifier = Modifier.weight(1f))
                PrimaryButton(text = "Fertig", onClick = { onConfirm(snapped) }, modifier = Modifier.weight(1f))
            }
        }
    }
}
