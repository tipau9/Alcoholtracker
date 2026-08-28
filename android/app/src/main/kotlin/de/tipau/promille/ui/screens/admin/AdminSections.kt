package de.tipau.promille.ui.screens.admin

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
    onBlockVoter: (String) -> Unit
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
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(item.subtitle, color = AppColors.textDim, fontSize = 12.sp)
                    Text(
                        "${item.itemType} · ${item.confirmedCount} Bestätigungen",
                        color = AppColors.textMuted,
                        fontSize = 11.sp
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onApprove) {
                    Text("Freigeben", color = AppColors.statusGreen, fontSize = 13.sp)
                }
                TextButton(onClick = onReject) {
                    Text("Ablehnen", color = AppColors.statusRed, fontSize = 13.sp)
                }
                // The contributing device id lives in the payload; without it
                // there is nobody to block, so the action is hidden entirely.
                contributorOf(item)?.let { voter ->
                    TextButton(onClick = { onBlockVoter(voter) }) {
                        Text("Voter sperren", color = AppColors.statusOrange, fontSize = 13.sp)
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
                fontSize = 13.sp
            )
        }
        Text(
            "$selectedCount ausgewählt",
            color = AppColors.textDim,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onApprove, enabled = selectedCount > 0) {
            Text("Freigeben", color = AppColors.statusGreen, fontSize = 13.sp)
        }
        TextButton(onClick = onReject, enabled = selectedCount > 0) {
            Text("Ablehnen", color = AppColors.statusRed, fontSize = 13.sp)
        }
    }
}

@Composable
fun AdminCatalogRow(item: AdminQueueItem) {
    PromilleCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.title, color = AppColors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(item.subtitle, color = AppColors.textDim, fontSize = 12.sp)
            }
            Text(
                item.status,
                color = when (item.status) {
                    "approved" -> AppColors.statusGreen
                    "rejected" -> AppColors.statusRed
                    else -> AppColors.statusOrange
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AdminReportRow(report: AdminReport, onResolve: (String) -> Unit) {
    PromilleCard(Modifier.fillMaxWidth()) {
        Column {
            Text(report.reason, color = AppColors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "${report.itemType} · ${report.status}",
                color = AppColors.textDim,
                fontSize = 12.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onResolve("resolved") }) {
                    Text("Erledigt", color = AppColors.statusGreen, fontSize = 13.sp)
                }
                TextButton(onClick = { onResolve("dismissed") }) {
                    Text("Verworfen", color = AppColors.textDim, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun AdminFlagRow(flag: AdminFeatureFlag, onToggle: (Boolean) -> Unit) {
    PromilleCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(flag.key, color = AppColors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (flag.isPublic) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .background(AppColors.accent.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("public", color = AppColors.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (flag.description.isNotEmpty()) {
                    Text(flag.description, color = AppColors.textDim, fontSize = 12.sp)
                }
            }
            Switch(
                checked = flag.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AppColors.background,
                    checkedTrackColor = AppColors.accent,
                    uncheckedThumbColor = AppColors.textDim,
                    uncheckedTrackColor = AppColors.card
                )
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
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
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
                            fontSize = 12.sp
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
                Text(voter.voter, color = AppColors.text, fontSize = 12.sp)
                if (voter.reason.isNotEmpty()) {
                    Text(voter.reason, color = AppColors.textDim, fontSize = 11.sp)
                }
            }
            TextButton(onClick = onUnblock) {
                Text("Entsperren", color = AppColors.accent, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun AdminAuditRow(entry: AdminAuditEntry) {
    PromilleCard(Modifier.fillMaxWidth()) {
        Column {
            Text(entry.action, color = AppColors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(entry.itemType, entry.itemID, entry.createdAtRaw).joinToString(" · "),
                color = AppColors.textDim,
                fontSize = 11.sp
            )
            entry.note?.takeIf { it.isNotEmpty() }?.let {
                Text(it, color = AppColors.textMuted, fontSize = 11.sp)
            }
        }
    }
}
