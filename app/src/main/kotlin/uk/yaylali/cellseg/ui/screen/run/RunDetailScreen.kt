package uk.yaylali.cellseg.ui.screen.run

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import uk.yaylali.cellseg.R
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunDetailScreen(
    runId: String,
    onBack: () -> Unit,
    onReRun: ((sampleId: String, imageUri: String) -> Unit)? = null,
    viewModel: RunDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showExportMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(runId) { viewModel.load(runId) }

    // Navigate back after delete
    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) onBack()
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.action_delete)) },
            text = { Text("This will permanently remove this run and its output files.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteRun()
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.run_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    // Re-run
                    uiState.run?.let { run ->
                        if (onReRun != null) {
                            IconButton(onClick = { onReRun(run.sampleId, run.originalImagePath) }) {
                                Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.action_rerun))
                            }
                        }
                    }
                    // Export picker
                    Box {
                        IconButton(onClick = { showExportMenu = true }) {
                            Icon(Icons.Outlined.IosShare, contentDescription = stringResource(R.string.action_export))
                        }
                        DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Export CSV (per cell)") },
                                onClick = { showExportMenu = false; viewModel.export(ExportFormat.CSV) },
                            )
                            DropdownMenuItem(
                                text = { Text("Export JSON") },
                                onClick = { showExportMenu = false; viewModel.export(ExportFormat.JSON) },
                            )
                        }
                    }
                    // Delete
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── 2-up crossfade viewer ───────────────────────────────────────
            val hasOriginal = uiState.originalPath != null
            val hasOutline = uiState.outlinePath != null
            if (hasOriginal || hasOutline) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                        ) {
                            // Original image underneath
                            uiState.originalPath?.let { path ->
                                ZoomableImage(
                                    file = File(path),
                                    contentDescription = stringResource(R.string.cd_original_image),
                                    modifier = Modifier.matchParentSize(),
                                )
                            }
                            // Outline overlay on top, alpha-controlled by slider
                            uiState.outlinePath?.let { path ->
                                ZoomableImage(
                                    file = File(path),
                                    contentDescription = stringResource(R.string.cd_outline_overlay),
                                    modifier = Modifier
                                        .matchParentSize()
                                        .alpha(uiState.overlayAlpha),
                                )
                            }
                        }
                        if (hasOriginal && hasOutline) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "Original",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Slider(
                                    value = uiState.overlayAlpha,
                                    onValueChange = { viewModel.setOverlayAlpha(it) },
                                    modifier = Modifier.weight(1f),
                                    valueRange = 0f..1f,
                                )
                                Text(
                                    "Overlay",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // ── Flows image (cloud only) ────────────────────────────────────
            uiState.flowsPath?.let { path ->
                if (File(path).exists()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Flow fields", style = MaterialTheme.typography.titleSmall)
                            ZoomableImage(
                                file = File(path),
                                contentDescription = stringResource(R.string.cd_flows_image),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                            )
                        }
                    }
                }
            }

            // ── Backend traceability ────────────────────────────────────────
            uiState.run?.let { run ->
                val tag = run.backendVersionTag.ifBlank { run.backendTier.name }
                Text(
                    text = stringResource(R.string.run_via, tag),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Metrics card ────────────────────────────────────────────────
            uiState.metrics?.let { m ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.run_detail_metrics_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            StatChip(
                                modifier = Modifier.weight(1f),
                                value = "${m.cellCount}",
                                label = stringResource(R.string.metric_cell_count),
                            )
                            StatChip(
                                modifier = Modifier.weight(1f),
                                value = String.format("%.1f%%", m.confluencePercent),
                                label = stringResource(R.string.metric_confluence),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            StatChip(
                                modifier = Modifier.weight(1f),
                                value = String.format("%.0f", m.meanCellAreaPx),
                                label = stringResource(R.string.metric_mean_area_px),
                            )
                            if (m.meanCellAreaUm2 != null) {
                                StatChip(
                                    modifier = Modifier.weight(1f),
                                    value = String.format("%.2f", m.meanCellAreaUm2),
                                    label = stringResource(R.string.metric_mean_area_um),
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                        m.estimatedDensityCellsPerCm2?.let { density ->
                            StatChip(
                                modifier = Modifier.fillMaxWidth(),
                                value = String.format("%.0f", density),
                                label = stringResource(R.string.density_label),
                            )
                        }
                    }
                }
            }

            uiState.deleteError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ZoomableImage(
    file: File,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset += panChange
    }
    LaunchedEffect(scale) {
        if (scale <= 1f) offset = Offset.Zero
    }

    androidx.compose.foundation.layout.Box(
        modifier = modifier.clipToBounds(),
    ) {
        AsyncImage(
            model = file,
            contentDescription = contentDescription,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .transformable(state = transformState),
        )
    }
}

@Composable
private fun StatChip(modifier: Modifier = Modifier, value: String, label: String) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

