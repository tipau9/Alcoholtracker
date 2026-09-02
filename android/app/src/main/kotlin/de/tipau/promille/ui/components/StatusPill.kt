package de.tipau.promille.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import de.tipau.promille.appSpec
import de.tipau.promille.bac.BacStatus
import de.tipau.promille.bac.StatusSkin
import de.tipau.promille.color

/**
 * Port of Views/Components/StatusPill.swift: coloured capsule badge for the
 * current BacStatus, shown under the Home BAC number and on crew cards.
 *
 * The icon is not decoration on iOS, it is how the status reads at a glance,
 * so the five SF Symbols are mapped one for one below.
 */
@Composable
fun StatusPill(
    status: BacStatus,
    skin: StatusSkin = StatusSkin.STANDARD,
    modifier: Modifier = Modifier
) {
    val color by animateColorAsState(
        targetValue = status.color,
        animationSpec = appSpec(spring()),
        label = "statusPillColor"
    )

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .border(0.5.dp, color.copy(alpha = 0.3f), CircleShape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (status == BacStatus.TIPSY) {
            // circle.fill has no Material equivalent and needs no path.
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        } else {
            Icon(
                imageVector = when (status) {
                    BacStatus.SOBER -> Icons.Filled.CheckCircle
                    BacStatus.DRUNK -> ExclamationCircle
                    BacStatus.CAREFUL -> Icons.Filled.Warning
                    else -> XOctagon
                },
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = status.label(skin),
            color = color,
            style = de.tipau.promille.AppText.captionBold
        )
    }
}

/** exclamationmark.circle.fill */
private val ExclamationCircle: ImageVector by lazy {
    ImageVector.Builder(
        name = "ExclamationCircle",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd) {
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
            reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
            reflectiveCurveTo(17.52f, 2f, 12f, 2f)
            close()
            moveTo(13f, 17f)
            horizontalLineTo(11f)
            verticalLineTo(15f)
            horizontalLineTo(13f)
            close()
            moveTo(13f, 13f)
            horizontalLineTo(11f)
            verticalLineTo(7f)
            horizontalLineTo(13f)
            close()
        }
    }.build()
}

/** xmark.octagon.fill */
private val XOctagon: ImageVector by lazy {
    ImageVector.Builder(
        name = "XOctagon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd) {
            moveTo(7.05f, 2f)
            horizontalLineToRelative(9.9f)
            lineTo(22f, 7.05f)
            verticalLineToRelative(9.9f)
            lineTo(16.95f, 22f)
            horizontalLineToRelative(-9.9f)
            lineTo(2f, 16.95f)
            verticalLineToRelative(-9.9f)
            close()
            moveTo(15.59f, 7f)
            lineTo(12f, 10.59f)
            lineTo(8.41f, 7f)
            lineTo(7f, 8.41f)
            lineTo(10.59f, 12f)
            lineTo(7f, 15.59f)
            lineTo(8.41f, 17f)
            lineTo(12f, 13.41f)
            lineTo(15.59f, 17f)
            lineTo(17f, 15.59f)
            lineTo(13.41f, 12f)
            lineTo(17f, 8.41f)
            close()
        }
    }.build()
}
