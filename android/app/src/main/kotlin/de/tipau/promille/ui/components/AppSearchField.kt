package de.tipau.promille.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.tipau.promille.AppColors
import de.tipau.promille.AppText
import io.github.alexzhirkevich.cupertino.CupertinoSearchTextField
import io.github.alexzhirkevich.cupertino.CupertinoSearchTextFieldDefaults
import io.github.alexzhirkevich.cupertino.ExperimentalCupertinoApi

/**
 * Native iOS CupertinoSearchTextField wrapper matching UISearchBar / .searchable.
 * Provides iOS magnifying glass, native clear button (xmark.circle.fill),
 * proper focused/unfocused pill styling and typography.
 */
@OptIn(ExperimentalCupertinoApi::class)
@Composable
fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "Suchen...",
    modifier: Modifier = Modifier
) {
    CupertinoSearchTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        textStyle = AppText.body.copy(color = AppColors.text),
        placeholder = {
            Text(
                text = placeholder,
                color = AppColors.textDim,
                style = AppText.body
            )
        },
        colors = CupertinoSearchTextFieldDefaults.colors(
            focusedContainerColor = AppColors.card,
            unfocusedContainerColor = AppColors.card,
            focusedTextColor = AppColors.text,
            unfocusedTextColor = AppColors.text,
            focusedPlaceholderColor = AppColors.textDim,
            unfocusedPlaceholderColor = AppColors.textDim,
            cursorColor = AppColors.accent,
            focusedLeadingIconColor = AppColors.textDim,
            unfocusedLeadingIconColor = AppColors.textDim,
            focusedTrailingIconColor = AppColors.textDim,
            unfocusedTrailingIconColor = AppColors.textDim
        )
    )
}
