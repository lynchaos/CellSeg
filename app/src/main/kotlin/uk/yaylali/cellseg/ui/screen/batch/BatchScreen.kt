package uk.yaylali.cellseg.ui.screen.batch

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import uk.yaylali.cellseg.R
import uk.yaylali.cellseg.domain.model.BackendTier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchScreen(
    onBack: () -> Unit,
    viewModel: BatchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    val pickImages = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        viewModel.addImages(uris.map { it.toString() })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.batch_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (uiState.items.any { it.status == BatchItemStatus.DONE }) {
                        IconButton(onClick = { viewModel.exportBatchCsv() }) {
                            Icon(Icons.Outlined.IosShare, contentDescription = stringResource(R.string.cd_export))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!uiState.isRunning && uiState.items.size < BATCH_MAX_IMAGES) {
                FloatingActionButton(onClick = { pickImages.launch("image/*") }) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.batch_add_images))
                }
            }
        },
        bottomBar = {
            if (uiState.items.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        if (uiState.modelNotDownloaded && uiState.selectedTier == BackendTier.LOCAL_CYTO3) {
                            Text(
                                stringResource(R.string.error_model_not_downloaded),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        uiState.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                        }
                        Button(
                            onClick = { viewModel.runBatch() },
                            enabled = !uiState.isRunning && !(uiState.modelNotDownloaded && uiState.selectedTier == BackendTier.LOCAL_CYTO3),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (uiState.isRunning) {
                                val done = uiState.items.count { it.status == BatchItemStatus.DONE || it.status == BatchItemStatus.FAILED }
                                Text("${done} / ${uiState.items.size} …")
                            } else {
                                Text(stringResource(R.string.batch_run_all, uiState.items.size))
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            // Backend picker
            item {
                val tiers = listOf(BackendTier.LOCAL_CYTO3, BackendTier.REMOTE_PUBLIC_HF)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.analyze_backend_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        tiers.forEachIndexed { idx, tier ->
                            SegmentedButton(
                                selected = uiState.selectedTier == tier,
                                onClick = { viewModel.setTier(tier) },
                                shape = SegmentedButtonDefaults.itemShape(idx, tiers.size),
                                label = { Text(tier.displayName, maxLines = 1) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Diameter slider
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(R.string.analyze_diameter_label),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "${uiState.params.diameter.toInt()} px",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = uiState.params.diameter.toFloat(),
                        onValueChange = { viewModel.setDiameter(it.toDouble()) },
                        valueRange = 5f..300f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // Image items
            itemsIndexed(uiState.items, key = { _, item -> item.uri }) { idx, item ->
                BatchItemRow(
                    item = item,
                    isCurrent = idx == uiState.currentIndex,
                    onRemove = { viewModel.removeImage(item.uri) },
                    enabled = !uiState.isRunning,
                )
            }

            if (uiState.items.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.batch_empty_hint, BATCH_MAX_IMAGES),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchItemRow(
    item: BatchItem,
    isCurrent: Boolean,
    onRemove: () -> Unit,
    enabled: Boolean,
) {
    val containerColor = when {
        isCurrent -> MaterialTheme.colorScheme.primaryContainer
        item.status == BatchItemStatus.DONE -> MaterialTheme.colorScheme.secondaryContainer
        item.status == BatchItemStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val name = item.uri.substringAfterLast("/")
                Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                when (item.status) {
                    BatchItemStatus.RUNNING ->
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    BatchItemStatus.DONE ->
                        Text(
                            "Cells: ${item.cellCount ?: "?"}  •  ${item.confluencePct?.let { "${it.toInt()}%" } ?: "—"}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    BatchItemStatus.FAILED ->
                        Text(item.error ?: "Failed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    else -> Text(item.status.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (enabled) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                }
            }
        }
    }
}
