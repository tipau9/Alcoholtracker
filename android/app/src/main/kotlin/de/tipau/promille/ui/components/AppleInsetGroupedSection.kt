package de.tipau.promille.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.tipau.promille.AppColors
import de.tipau.promille.AppText
import io.github.alexzhirkevich.cupertino.ExperimentalCupertinoApi
import io.github.alexzhirkevich.cupertino.section.CupertinoSection
import io.github.alexzhirkevich.cupertino.section.SectionItem
import io.github.alexzhirkevich.cupertino.section.SectionStyle

/**
 * 1:1 Apple Inset-Grouped Section matching iOS UITableView(style: .insetGrouped)
 * and SwiftUI List with .insetGrouped style. Powered by alexzhirkevich:cupertino.
 */
@OptIn(ExperimentalCupertinoApi::class)
@Composable
fun AppleInsetGroupedSection(
    title: String? = null,
    caption: String? = null,
    modifier: Modifier = Modifier,
    titleTrailingContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    CupertinoSection(
        modifier = modifier.fillMaxWidth(),
        style = SectionStyle.InsetGrouped,
        color = AppColors.card,
        title = if (title != null) {
            {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title.uppercase(),
                        style = AppText.captionBold,
                        color = AppColors.textDim
                    )
                    titleTrailingContent?.invoke()
                }
            }
        } else null,
        caption = caption?.let {
            {
                Text(
                    text = it,
                    style = AppText.micro,
                    color = AppColors.textDim,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    ) {
        SectionItem(
            paddingValues = PaddingValues(0.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}
