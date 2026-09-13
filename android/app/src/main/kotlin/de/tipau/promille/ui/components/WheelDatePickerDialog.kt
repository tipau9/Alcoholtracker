package de.tipau.promille.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.commandiron.wheel_picker_compose.WheelDatePicker
import de.tipau.promille.AppColors
import java.time.LocalDate

/**
 * Native iOS UIDatePicker in "wheels" style matching WheelTimePickerDialog.
 * Provides smooth 3D tumbler rolling with inertia, authentic typography and
 * Apple Primary/Secondary action buttons ("Abbrechen" / "Fertig").
 */
@Composable
fun WheelDatePickerDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
    minDate: LocalDate = LocalDate.of(1920, 1, 1),
    maxDate: LocalDate = LocalDate.now()
) {
    var snapped by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(AppColors.card, RoundedCornerShape(20.dp))
                .appleLichtkante(cornerRadius = 20.dp)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WheelDatePicker(
                startDate = initial,
                minDate = minDate,
                maxDate = maxDate,
                size = DpSize(260.dp, 160.dp),
                textColor = AppColors.text,
                onSnappedDate = { snapped = it }
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SecondaryButton(
                    text = "Abbrechen",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                PrimaryButton(
                    text = "Fertig",
                    onClick = { onConfirm(snapped) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
