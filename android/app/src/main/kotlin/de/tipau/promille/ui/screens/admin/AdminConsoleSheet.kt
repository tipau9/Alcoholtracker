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

    // The 5 admin editor sheets (AdminEditors.kt) - AdminView.swift's
    // showFlagEditor/editingDrink/editingMix/showRoleEditor/showBlockEditor.
    var editingFlag by remember { mutableStateOf<de.tipau.promille.network.AdminFeatureFlag?>(null) }
    var showFlagEditor by remember { mutableStateOf(false) }
    var editingDrink by remember { mutableStateOf<de.tipau.promille.network.AdminQueueItem?>(null) }
    var editingMix by remember { mutableStateOf<de.tipau.promille.network.AdminQueueItem?>(null) }
    var showRoleEditor by remember { mutableStateOf(false) }
    var showBlockEditor by remember { mutableStateOf(false) }

    // iOS: edit(_:) dispatches on item.itemType (AdminView.swift:555-561).
    fun editQueueItem(item: de.tipau.promille.network.AdminQueueItem) {
        when (item.itemType) {
            "drink" -> editingDrink = item
            "mix" -> editingMix = item
        }
    }

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
                    // iOS: .appHeadline
                    Text(
                        "Admin",
                        color = AppColors.text,
                        style = de.tipau.promille.AppText.headline
                    )
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = AppColors.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        TextButton(onClick = { viewModel.reloadAll() }) {
                            // iOS: .appCaption
                            Text("Neu laden", color = AppColors.accent, style = de.tipau.promille.AppText.caption)
                        }
                    }
                }
            }

            if (!isAdmin) {
                item {
                    // iOS: .appCaption
                    Text(
                        "Kein Adminzugang für dieses Konto. Die Serverabschnitte bleiben leer, die Debug-Werkzeuge funktionieren trotzdem.",
                        color = AppColors.statusOrange,
                        style = de.tipau.promille.AppText.caption
                    )
                }
            }

            error?.let { message ->
                item { Text(message, color = AppColors.statusRed, style = de.tipau.promille.AppText.caption) }
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
                                            style = de.tipau.promille.AppText.headline.merge(de.tipau.promille.TabularFigures)
                                        )
                                        // iOS: .appMicro
                                        Text(
                                            AdminViewModel.metricLabel(metric.metric),
                                            color = AppColors.textDim,
                                            style = de.tipau.promille.AppText.micro
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
                            // iOS: .appCaptionBold
                            Text(
                                candidate.title,
                                color = if (active) AppColors.accent else AppColors.textDim,
                                style = de.tipau.promille.AppText.captionBold
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
                                onBlockVoter = { viewModel.blockVoter(it) },
                                onEdit = if (item.itemType == "drink" || item.itemType == "mix") {
                                    { editQueueItem(item) }
                                } else null
                            )
                        }
                    }
                }

                AdminSection.CATALOG -> {
                    item {
                        de.tipau.promille.ui.components.AppTextField(
                            value = searchTerm,
                            onValueChange = { searchTerm = it },
                            placeholder = "Suchen",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (catalog.isEmpty()) {
                        item { AdminEmpty("Kein Eintrag gefunden.") }
                    } else {
                        items(catalog, key = { it.id }) { item ->
                            AdminCatalogRow(
                                item = item,
                                onEdit = if (item.itemType == "drink" || item.itemType == "mix") {
                                    { editQueueItem(item) }
                                } else null
                            )
                        }
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
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionLabel("FEATURE FLAGS")
                            // iOS: "plus.circle.fill" opens AdminFlagEditor(flag: nil) (swift:321-329).
                            IconButton(onClick = { editingFlag = null; showFlagEditor = true }) {
                                Icon(Icons.Filled.AddCircle, contentDescription = "Flag anlegen", tint = AppColors.accent)
                            }
                        }
                    }
                    if (flags.isEmpty()) {
                        item { AdminEmpty("Keine Feature Flags.") }
                    } else {
                        items(flags, key = { it.key }) { flag ->
                            AdminFlagRow(flag) { editingFlag = flag; showFlagEditor = true }
                        }
                    }
                }

                AdminSection.SECURITY -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionLabel("ADMIN-ROLLEN")
                            // iOS: "person.badge.plus" opens AdminRoleEditor (swift:344-352).
                            IconButton(onClick = { showRoleEditor = true }) {
                                Icon(Icons.Filled.Add, contentDescription = "Rolle setzen", tint = AppColors.accent)
                            }
                        }
                    }
                    if (adminUsers.isEmpty()) {
                        item { AdminEmpty("Keine Rollen vergeben.") }
                    } else {
                        items(adminUsers, key = { it.userID }) { user ->
                            AdminUserRow(user) { viewModel.setUserRole(user.userID, it) }
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionLabel("BLOCKLIST")
                            // iOS: "hand.raised.fill" opens AdminBlockEditor (swift:370-378).
                            IconButton(onClick = { showBlockEditor = true }) {
                                Icon(Icons.Filled.Add, contentDescription = "Blockieren", tint = AppColors.accent)
                            }
                        }
                    }
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

    if (showFlagEditor) {
        AdminFlagEditorDialog(
            flag = editingFlag,
            onDismiss = { showFlagEditor = false },
            onSave = { key, enabled, isPublic, value, description ->
                viewModel.saveFlag(key, enabled, isPublic, value, description)
            }
        )
    }
    editingDrink?.let { item ->
        AdminDrinkEditorDialog(
            item = item,
            onDismiss = { editingDrink = null },
            onSave = { name, category, volume, abv, calories, iconName ->
                viewModel.updateDrink(item.id, name, category, volume, abv, calories, iconName)
            }
        )
    }
    editingMix?.let { item ->
        AdminMixEditorDialog(
            item = item,
            onDismiss = { editingMix = null },
            onSave = { name, ingredients, totalVolume, totalAbv, calories ->
                viewModel.updateMix(item.id, name, ingredients, totalVolume, totalAbv, calories)
            }
        )
    }
    if (showRoleEditor) {
        AdminRoleEditorDialog(
            onDismiss = { showRoleEditor = false },
            onSave = { userID, role -> viewModel.setRole(userID, role) }
        )
    }
    if (showBlockEditor) {
        AdminBlockEditorDialog(
            onDismiss = { showBlockEditor = false },
            onSave = { voter, reason -> viewModel.blockVoterAwait(voter, reason) }
        )
    }
}

@Composable
private fun AdminEmpty(text: String) {
    // iOS: .appCaption
    Text(
        text,
        color = AppColors.textMuted,
        style = de.tipau.promille.AppText.caption,
        modifier = Modifier.padding(vertical = 20.dp)
    )
}
