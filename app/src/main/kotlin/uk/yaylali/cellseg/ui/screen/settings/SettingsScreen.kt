package uk.yaylali.cellseg.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import uk.yaylali.cellseg.R
import uk.yaylali.cellseg.domain.model.BackendTier
import uk.yaylali.cellseg.domain.model.CalibrationEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onModelManagement: () -> Unit,
    onAttribution: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var tokenDraft by remember { mutableStateOf("") }
    var slugDraft by remember(uiState.customSpaceSlug) { mutableStateOf(uiState.customSpaceSlug) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddCalibration by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {

            // ── Segmentation Defaults ─────────────────────────────────────────
            SettingsSection(title = "Segmentation Defaults") {
                SettingSliderRow(
                    label = "Cell diameter",
                    valueText = "${uiState.diameter.toInt()} px",
                    value = uiState.diameter.toFloat(),
                    onValueChange = { viewModel.setDiameter(it.toDouble()) },
                    valueRange = 5f..300f,
                    minLabel = "5 px",
                    maxLabel = "300 px",
                    hint = "Expected cell size. Default: 30 px.",
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
                SettingSliderRow(
                    label = "Flow threshold",
                    valueText = String.format("%.2f", uiState.flowThreshold),
                    value = uiState.flowThreshold,
                    onValueChange = { viewModel.setFlowThreshold(it) },
                    valueRange = 0f..3f,
                    minLabel = "0.00",
                    maxLabel = "3.00",
                    hint = "Higher values allow more cell shapes. Default: 0.4.",
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
                SettingSliderRow(
                    label = "Cell probability",
                    valueText = String.format("%.1f", uiState.cellProbThreshold),
                    value = uiState.cellProbThreshold,
                    onValueChange = { viewModel.setCellProbThreshold(it) },
                    valueRange = -6f..6f,
                    minLabel = "−6",
                    maxLabel = "+6",
                    hint = "Lower values detect more cells. Default: 0.0.",
                )
            }

            // ── Backend ───────────────────────────────────────────────────────
            SettingsSection(title = "Backend") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Default backend",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val tiers = listOf(BackendTier.LOCAL_CYTO3, BackendTier.REMOTE_PUBLIC_HF)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        tiers.forEachIndexed { idx, tier ->
                            SegmentedButton(
                                selected = uiState.defaultTier == tier,
                                onClick = { viewModel.setDefaultTier(tier) },
                                shape = SegmentedButtonDefaults.itemShape(idx, tiers.size),
                                label = { Text(tier.displayName, maxLines = 1) },
                            )
                        }
                    }
                }

                if (uiState.defaultTier.isRemote) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-fallback to on-device", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Switch to local processing when cloud quota is exceeded",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = uiState.autoFallback,
                            onCheckedChange = viewModel::setAutoFallback,
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Custom HF Space slug", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Override the default mouseland/cellpose space",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = slugDraft,
                            onValueChange = { slugDraft = it },
                            placeholder = { Text("e.g. owner/space-name") },
                            singleLine = true,
                            trailingIcon = {
                                if (slugDraft != uiState.customSpaceSlug) {
                                    IconButton(onClick = { viewModel.setCustomSpaceSlug(slugDraft) }) {
                                        Icon(Icons.Outlined.Check, contentDescription = "Save")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // ── Hugging Face ──────────────────────────────────────────────────
            if (uiState.defaultTier.isRemote) {
                SettingsSection(title = "Hugging Face") {
                    if (uiState.hasToken) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.settings_token_set),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    "Stored encrypted on device",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(onClick = { viewModel.clearToken() }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.settings_token_clear))
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Add a token to increase rate limits on public HF Spaces.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedTextField(
                                value = tokenDraft,
                                onValueChange = { tokenDraft = it },
                                label = { Text(stringResource(R.string.settings_hf_token_hint)) },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = { viewModel.saveToken(tokenDraft); tokenDraft = "" },
                                enabled = tokenDraft.isNotBlank(),
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Text(stringResource(R.string.settings_token_save))
                            }
                        }
                    }
                }
            }

            // ── Calibration ───────────────────────────────────────────────────
            SettingsSection(title = stringResource(R.string.settings_calibration)) {
                uiState.calibrations.forEachIndexed { idx, entry ->
                    if (idx > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.magnificationLabel, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${entry.pxPerMicron} px/µm · FOV ${entry.fovWidthMm}×${entry.fovHeightMm} mm",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { viewModel.removeCalibration(entry) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (uiState.calibrations.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
                }
                TextButton(
                    onClick = { showAddCalibration = true },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.settings_calibration_add))
                }
            }

            // ── Data Management ───────────────────────────────────────────────
            SettingsSection(title = stringResource(R.string.settings_data_management)) {
                Text(
                    "${uiState.storageUsedMb.toInt()} MB used",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.clearCache() },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.settings_clear_cache)) }
                    OutlinedButton(
                        onClick = { viewModel.exportAllData() },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.settings_export_all)) }
                }
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_delete_all)) }
            }

            // ── About ─────────────────────────────────────────────────────────
            SettingsSection(title = stringResource(R.string.settings_about)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Version", style = MaterialTheme.typography.bodyMedium)
                    Text(uiState.versionName, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
                val sourceUrl = stringResource(R.string.source_repo_url)
                SettingsNavRow(label = "Source code (GitHub)", onClick = { uriHandler.openUri(sourceUrl) })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
                val privacyUrl = stringResource(R.string.privacy_url)
                SettingsNavRow(label = "Privacy Policy", onClick = { uriHandler.openUri(privacyUrl) })
            }

            // ── App ───────────────────────────────────────────────────────────
            SettingsSection(title = "App") {
                SettingsNavRow(
                    label = stringResource(R.string.settings_model_management),
                    onClick = onModelManagement,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
                SettingsNavRow(
                    label = stringResource(R.string.settings_attribution),
                    onClick = onAttribution,
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ── Delete all confirm ─────────────────────────────────────────────────
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.settings_delete_all)) },
            text = { Text(stringResource(R.string.settings_delete_all_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllData()
                    showDeleteConfirm = false
                }) { Text(stringResource(R.string.cd_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    // ── Add calibration ────────────────────────────────────────────────────
    if (showAddCalibration) {
        AddCalibrationDialog(
            onDismiss = { showAddCalibration = false },
            onConfirm = { entry ->
                viewModel.addCalibration(entry)
                showAddCalibration = false
            },
        )
    }
}

// ── Reusable components ────────────────────────────────────────────────────────

@Composable
private fun AddCalibrationDialog(
    onDismiss: () -> Unit,
    onConfirm: (CalibrationEntry) -> Unit,
) {
    var magnLabel by remember { mutableStateOf("") }
    var pxPerMicron by remember { mutableStateOf("") }
    var fovWidth by remember { mutableStateOf("") }
    var fovHeight by remember { mutableStateOf("") }

    val isValid = magnLabel.isNotBlank() &&
        pxPerMicron.toFloatOrNull() != null &&
        fovWidth.toFloatOrNull() != null &&
        fovHeight.toFloatOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_calibration_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = magnLabel,
                    onValueChange = { magnLabel = it },
                    label = { Text("Magnification (e.g. 10×)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pxPerMicron,
                    onValueChange = { pxPerMicron = it },
                    label = { Text("px / µm") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fovWidth,
                        onValueChange = { fovWidth = it },
                        label = { Text("FOV width mm") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = fovHeight,
                        onValueChange = { fovHeight = it },
                        label = { Text("FOV height mm") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(CalibrationEntry(
                        magnificationLabel = magnLabel,
                        pxPerMicron = pxPerMicron.toFloat(),
                        fovWidthMm = fovWidth.toFloat(),
                        fovHeightMm = fovHeight.toFloat(),
                    ))
                },
                enabled = isValid,
            ) { Text(stringResource(R.string.cd_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun SettingSliderRow(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    minLabel: String,
    maxLabel: String,
    hint: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                valueText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(minLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(maxLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsNavRow(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Icon(
                Icons.Outlined.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

