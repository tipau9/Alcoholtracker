package de.tipau.promille.ui.screens.admin
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.di.AppContainer
import de.tipau.promille.ui.components.SectionLabel
import de.tipau.promille.ui.viewmodels.AdminSection
import de.tipau.promille.ui.viewmodels.AdminViewModel

/**
 * Port of AdminView.swift. Seven sections behind the server side admin role: the
 * moderation queue, the approved catalog, user reports, feature flags, roles and
 * the voter blocklist, the audit log, and the local debug tooling.
 *
 * The role check below only decides what to render. Every list comes from a
 * SECURITY DEFINER RPC that checks the caller again, so hiding a tab is
 * convenience, never the protection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminConsoleSheet(
    container: AppContainer,
    onDismiss: () -> Unit
) {
    val viewModel = remember { AdminViewModel(container.supabase) }
    var section by remember { mutableStateOf(AdminSection.MODERATION) }

    val metrics by viewModel.metrics.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val catalog by viewModel.catalog.collectAsState()
    val reports by viewModel.reports.collectAsState()
    val flags by viewModel.flags.collectAsState()
    val adminUsers by viewModel.adminUsers.collectAsState()
    val blockedVoters by viewModel.blockedVoters.collectAsState()
    val audit by viewModel.audit.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isAdmin by container.supabase.isAdmin.collectAsState()

    var searchTerm by remember { mutableStateOf("") }

    // Debounced: the field fires per keystroke, and two responses landing out
    // of order would settle the list on a prefix of what was typed.
    LaunchedEffect(searchTerm) {
        if (searchTerm != viewModel.catalogSearch.value) {
            kotlinx.coroutines.delay(350)
            viewModel.searchCatalog(searchTerm)
        }
    }

    LaunchedEffect(isAdmin) {
        if (isAdmin) viewModel.reloadAll()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp, top = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(AppColors.background)
                .border(0.5.dp, AppColors.border, RoundedCornerShape(24.dp))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 20.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Admin",
                        color = AppColors.text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = AppColors.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        TextButton(onClick = { viewModel.reloadAll() }) {
                            Text("Neu laden", color = AppColors.accent, fontSize = 13.sp)
                        }
                    }
                }
            }

            if (!isAdmin) {
                item {
                    Text(
                        "Kein Adminzugang für dieses Konto. Die Serverabschnitte bleiben leer, die Debug-Werkzeuge funktionieren trotzdem.",
                        color = AppColors.statusOrange,
                        fontSize = 12.sp
                    )
                }
            }

            error?.let { message ->
                item { Text(message, color = AppColors.statusRed, fontSize = 12.sp) }
            }

            if (metrics.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        metrics.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                pair.forEach { metric ->
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(AppColors.card, RoundedCornerShape(12.dp))
                                            .border(0.5.dp, AppColors.border, RoundedCornerShape(12.dp))
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            "${metric.value}",
                                            color = AppColors.accent,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            AdminViewModel.metricLabel(metric.metric),
                                            color = AppColors.textDim,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AdminSection.entries) { candidate ->
                        val active = candidate == section
                        Box(
                            modifier = Modifier
                                .background(
                                    if (active) AppColors.accent.copy(alpha = 0.15f) else AppColors.card,
                                    RoundedCornerShape(10.dp)
                                )
                                .border(
                                    0.5.dp,
                                    if (active) AppColors.accent else AppColors.border,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { section = candidate }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                candidate.title,
                                color = if (active) AppColors.accent else AppColors.textDim,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            when (section) {
                AdminSection.MODERATION -> {
                    if (queue.isEmpty()) {
                        item { AdminEmpty("Keine offenen Produkte oder Mixes.") }
                    } else {
                        item {
                            AdminBulkActionBar(
                                selectedCount = selection.size,
                                allSelected = selection.size == queue.size,
                                onToggleAll = { viewModel.toggleSelectAll() },
                                onApprove = { viewModel.bulkSetModerationStatus("approved") },
                                onReject = { viewModel.bulkSetModerationStatus("rejected") }
                            )
                        }
                        items(queue, key = { it.id }) { item ->
                            AdminQueueRow(
                                item = item,
                                isSelected = item.id in selection,
                                onToggleSelection = { viewModel.toggleSelection(item.id) },
                                onApprove = { viewModel.setModerationStatus(item, "approved") },
                                onReject = { viewModel.setModerationStatus(item, "rejected") },
                                onBlockVoter = { viewModel.blockVoter(it) }
                            )
                        }
                    }
                }

                AdminSection.CATALOG -> {
                    item {
                        OutlinedTextField(
                            value = searchTerm,
                            onValueChange = { searchTerm = it },
                            placeholder = { Text("Suchen", color = AppColors.textMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = AppColors.text,
                                unfocusedTextColor = AppColors.text,
                                focusedBorderColor = AppColors.accent,
                                unfocusedBorderColor = AppColors.border,
                                cursorColor = AppColors.accent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (catalog.isEmpty()) {
                        item { AdminEmpty("Kein Eintrag gefunden.") }
                    } else {
                        items(catalog, key = { it.id }) { AdminCatalogRow(it) }
                    }
                }

                AdminSection.REPORTS -> {
                    if (reports.isEmpty()) {
                        item { AdminEmpty("Keine offenen Meldungen.") }
                    } else {
                        items(reports, key = { it.id }) { report ->
                            AdminReportRow(report) { viewModel.resolveReport(report.id, it) }
                        }
                    }
                }

                AdminSection.FLAGS -> {
                    if (flags.isEmpty()) {
                        item { AdminEmpty("Keine Feature Flags.") }
                    } else {
                        items(flags, key = { it.key }) { flag ->
                            AdminFlagRow(flag) { viewModel.setFlag(flag, it) }
                        }
                    }
                }

                AdminSection.SECURITY -> {
                    item { SectionLabel("ADMIN-ROLLEN") }
                    if (adminUsers.isEmpty()) {
                        item { AdminEmpty("Keine Rollen vergeben.") }
                    } else {
                        items(adminUsers, key = { it.userID }) { user ->
                            AdminUserRow(user) { viewModel.setUserRole(user.userID, it) }
                        }
                    }
                    item { SectionLabel("BLOCKLIST") }
                    if (blockedVoters.isEmpty()) {
                        item { AdminEmpty("Niemand gesperrt.") }
                    } else {
                        items(blockedVoters, key = { it.voter }) { voter ->
                            AdminBlockedVoterRow(voter) { viewModel.unblockVoter(voter.voter) }
                        }
                    }
                }

                AdminSection.AUDIT -> {
                    if (audit.isEmpty()) {
                        item { AdminEmpty("Noch keine Einträge.") }
                    } else {
                        items(audit, key = { it.id }) { AdminAuditRow(it) }
                    }
                }

                AdminSection.DEBUG -> {
                    item { AdminDebugSection(container) }
                }
            }
        }
    }
}
}

@Composable
private fun AdminEmpty(text: String) {
    Text(
        text,
        color = AppColors.textMuted,
        fontSize = 13.sp,
        modifier = Modifier.padding(vertical = 20.dp)
    )
}
