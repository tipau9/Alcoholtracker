package de.tipau.promille.ui.screens.admin
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.network.AdminAuditEntry
import de.tipau.promille.network.AdminBlockedVoter
import de.tipau.promille.network.AdminFeatureFlag
import de.tipau.promille.network.AdminQueueItem
import de.tipau.promille.network.AdminReport
import de.tipau.promille.network.AdminUserRole
import de.tipau.promille.ui.components.PromilleCard
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One moderation candidate: tick it for a bulk action or decide it on the spot. */
@Composable
fun AdminQueueRow(
    item: AdminQueueItem,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onBlockVoter: (String) -> Unit,
    onEdit: (() -> Unit)? = null
) {
    PromilleCard(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = AppColors.accent,
                        uncheckedColor = AppColors.border,
                        checkmarkColor = AppColors.background
                    )
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        color = AppColors.text,
                        style = de.tipau.promille.AppText.bodyBold
                    )
                    Text(item.subtitle, color = AppColors.textDim, style = de.tipau.promille.AppText.caption)
                    Text(
                        "${item.itemType} · ${item.confirmedCount} Bestätigungen",
                        color = AppColors.textMuted,
                        style = de.tipau.promille.AppText.micro
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onApprove) {
                    Text("Freigeben", color = AppColors.statusGreen, style = de.tipau.promille.AppText.captionBold)
                }
                TextButton(onClick = onReject) {
                    Text("Ablehnen", color = AppColors.statusRed, style = de.tipau.promille.AppText.captionBold)
                }
                // iOS: pencil "Edit" button opens AdminDrinkEditor/AdminMixEditor for
                // this item (AdminView.swift:826-829).
                onEdit?.let {
                    TextButton(onClick = it) {
                        Text("Bearbeiten", color = AppColors.textDim, style = de.tipau.promille.AppText.captionBold)
                    }
                }
                // The contributing device id lives in the payload; without it
                // there is nobody to block, so the action is hidden entirely.
                contributorOf(item)?.let { voter ->
                    TextButton(onClick = { onBlockVoter(voter) }) {
                        Text("Voter sperren", color = AppColors.statusOrange, style = de.tipau.promille.AppText.captionBold)
                    }
                }
            }
        }
    }
}

private fun contributorOf(item: AdminQueueItem): String? {
    val payload = item.payload as? JsonObject ?: return null
    val raw = (payload["contributed_by"] ?: payload["voter"]) ?: return null
    return runCatching { raw.jsonPrimitive.content }.getOrNull()?.takeIf { it.isNotBlank() }
}

@Composable
fun AdminBulkActionBar(
    selectedCount: Int,
    allSelected: Boolean,
    onToggleAll: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.card, RoundedCornerShape(12.dp))
            .border(0.5.dp, AppColors.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        TextButton(onClick = onToggleAll) {
            Text(
                if (allSelected) "Keine" else "Alle",
                color = AppColors.accent,
                style = de.tipau.promille.AppText.captionBold
            )
        }
        Text(
            "$selectedCount ausgewählt",
            color = AppColors.textDim,
            style = de.tipau.promille.AppText.caption,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onApprove, enabled = selectedCount > 0) {
            Text("Freigeben", color = AppColors.statusGreen, style = de.tipau.promille.AppText.captionBold)
        }
        TextButton(onClick = onReject, enabled = selectedCount > 0) {
            Text("Ablehnen", color = AppColors.statusRed, style = de.tipau.promille.AppText.captionBold)
        }
    }
}

@Composable
fun AdminCatalogRow(item: AdminQueueItem, onEdit: (() -> Unit)? = null) {
    PromilleCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.title, color = AppColors.text, style = de.tipau.promille.AppText.bodyBold)
                Text(item.subtitle, color = AppColors.textDim, style = de.tipau.promille.AppText.caption)
            }
            Text(
                item.status,
                color = when (item.status) {
                    "approved" -> AppColors.statusGreen
                    "rejected" -> AppColors.statusRed
                    else -> AppColors.statusOrange
                },
                style = de.tipau.promille.AppText.captionBold
            )
            // iOS: pencil "Edit" button (AdminView.swift:826-829).
            onEdit?.let {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = it) {
                    Text("Bearbeiten", color = AppColors.textDim, style = de.tipau.promille.AppText.captionBold)
                }
            }
        }
    }
}

@Composable
fun AdminReportRow(report: AdminReport, onResolve: (String) -> Unit) {
    PromilleCard(Modifier.fillMaxWidth()) {
        Column {
            Text(report.reason, color = AppColors.text, style = de.tipau.promille.AppText.bodyBold)
            Text(
                "${report.itemType} · ${report.status}",
                color = AppColors.textDim,
                style = de.tipau.promille.AppText.caption
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onResolve("resolved") }) {
                    Text("Erledigt", color = AppColors.statusGreen, style = de.tipau.promille.AppText.captionBold)
                }
                TextButton(onClick = { onResolve("dismissed") }) {
                    Text("Verworfen", color = AppColors.textDim, style = de.tipau.promille.AppText.captionBold)
                }
            }
        }
    }
}

/** iOS: the whole row is a Button that opens AdminFlagEditor - there's no quick
 * toggle here, editing (including flipping "Aktiv") happens in the sheet
 * (AdminView.swift:948-978). */
@Composable
fun AdminFlagRow(flag: AdminFeatureFlag, onEdit: () -> Unit) {
    PromilleCard(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (flag.enabled) AppColors.statusGreen else AppColors.textMuted.copy(alpha = 0.35f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(flag.key, color = AppColors.text, style = de.tipau.promille.AppText.bodyBold)
                    if (flag.isPublic) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .background(AppColors.accent.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("public", color = AppColors.accent, style = de.tipau.promille.AppText.micro)
                        }
                    }
                }
                if (flag.description.isNotEmpty()) {
                    Text(flag.description, color = AppColors.textDim, style = de.tipau.promille.AppText.caption)
                }
            }
            Icon(
                imageVector = de.tipau.promille.ui.components.AppIcons.ChevronRight,
                contentDescription = null,
                tint = AppColors.textMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun AdminUserRow(user: AdminUserRole, onSetRole: (String) -> Unit) {
    val roles = listOf("user", "moderator", "admin")
    PromilleCard(Modifier.fillMaxWidth()) {
        Column {
            Text(
                user.userID,
                color = AppColors.text,
                style = de.tipau.promille.AppText.caption
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                roles.forEach { role ->
                    val active = role == user.role
                    Box(
                        modifier = Modifier
                            .background(
                                if (active) AppColors.accent.copy(alpha = 0.15f) else AppColors.background,
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                0.5.dp,
                                if (active) AppColors.accent else AppColors.border,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable(enabled = !active) { onSetRole(role) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            role,
                            color = if (active) AppColors.accent else AppColors.textDim,
                            style = de.tipau.promille.AppText.caption
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdminBlockedVoterRow(voter: AdminBlockedVoter, onUnblock: () -> Unit) {
    PromilleCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(voter.voter, color = AppColors.text, style = de.tipau.promille.AppText.caption)
                if (voter.reason.isNotEmpty()) {
                    Text(voter.reason, color = AppColors.textDim, style = de.tipau.promille.AppText.micro)
                }
            }
            TextButton(onClick = onUnblock) {
                Text("Entsperren", color = AppColors.accent, style = de.tipau.promille.AppText.captionBold)
            }
        }
    }
}

@Composable
fun AdminAuditRow(entry: AdminAuditEntry) {
    PromilleCard(Modifier.fillMaxWidth()) {
        Column {
            Text(entry.action, color = AppColors.text, style = de.tipau.promille.AppText.captionBold)
            Text(
                listOfNotNull(entry.itemType, entry.itemID, entry.createdAtRaw).joinToString(" · "),
                color = AppColors.textDim,
                style = de.tipau.promille.AppText.micro
            )
            entry.note?.takeIf { it.isNotEmpty() }?.let {
                Text(it, color = AppColors.textMuted, style = de.tipau.promille.AppText.micro)
            }
        }
    }
}
