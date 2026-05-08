package uk.yaylali.cellseg.ui.screen.analyze

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import uk.yaylali.cellseg.R
import uk.yaylali.cellseg.domain.model.BackendTier
import uk.yaylali.cellseg.domain.model.NamedPreset
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzeScreen(
    sampleId: String,
    imageUri: String,
    onRunStarted: (runId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: AnalyzeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var showPresetsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(sampleId, imageUri) {
        viewModel.load(sampleId, imageUri)
    }

    LaunchedEffect(uiState.startedRunId) {
        uiState.startedRunId?.let { onRunStarted(it) }
    }

    // Presets bottom sheet
    if (showPresetsSheet && uiState.presets.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = { showPresetsSheet = false }) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    stringResource(R.string.params_presets),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                uiState.presets.forEach { preset ->
                    ListItem(
                        headlineContent = { Text(preset.name) },
                        supportingContent = { Text("diameter=${preset.params.diameter.toInt()} px") },
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                    HorizontalDivider()
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.analyze_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (uiState.presets.isNotEmpty()) {
                        TextButton(onClick = { showPresetsSheet = true }) {
                            Text(stringResource(R.string.params_presets))
                        }
                    }
                    IconButton(onClick = { viewModel.resetParams() }) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = stringResource(R.string.params_reset))
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    // Model not downloaded warning
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
                        onClick = { viewModel.startRun() },
                        enabled = !uiState.isStarting && !(uiState.modelNotDownloaded && uiState.selectedTier == BackendTier.LOCAL_CYTO3),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (uiState.isStarting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else if (uiState.modelNotDownloaded && uiState.selectedTier == BackendTier.LOCAL_CYTO3) {
                            Text(stringResource(R.string.download_model_cta))
                        } else {
                            Text(stringResource(R.string.analyze_run_button))
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // Image preview
            if (imageUri.isNotBlank()) {
                AsyncImage(
                    model = File(imageUri),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }

            // Parameters card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        stringResource(R.string.analyze_title),
                        style = MaterialTheme.typography.titleMedium,
                    )

                    // Diameter
                    ParamSlider(
                        label = stringResource(R.string.analyze_diameter_label),
                        valueText = "${uiState.params.diameter.toInt()} px",
                        value = uiState.params.diameter.toFloat(),
                        onValueChange = { viewModel.setDiameter(it.toDouble()) },
                        valueRange = 5f..300f,
                        minLabel = "5 px",
                        maxLabel = "300 px",
                    )

                    HorizontalDivider()

                    // Flow threshold
                    ParamSlider(
                        label = stringResource(R.string.analyze_flow_threshold_label),
                        valueText = String.format("%.2f", uiState.params.flowThreshold),
                        value = uiState.params.flowThreshold.toFloat(),
                        onValueChange = { viewModel.setFlowThreshold(it.toDouble()) },
                        valueRange = 0f..3f,
                        minLabel = "0.00",
                        maxLabel = "3.00",
                    )

                    HorizontalDivider()

                    // Cell probability threshold
                    ParamSlider(
                        label = stringResource(R.string.analyze_cellprob_label),
                        valueText = String.format("%.1f", uiState.params.cellProbThreshold),
                        value = uiState.params.cellProbThreshold.toFloat(),
                        onValueChange = { viewModel.setCellProbThreshold(it.toDouble()) },
                        valueRange = -6f..6f,
                        minLabel = "−6",
                        maxLabel = "+6",
                    )

                    HorizontalDivider()

                    // Max resize
                    ParamSlider(
                        label = stringResource(R.string.params_max_resize),
                        valueText = "${uiState.params.maxResize} px",
                        value = uiState.params.maxResize.toFloat(),
                        onValueChange = { viewModel.setMaxResize(it.toInt()) },
                        valueRange = 256f..2000f,
                        minLabel = "256",
                        maxLabel = "2000",
                    )
                }
            }

            // Backend selector
            val supportedTiers = BackendTier.entries.filter {
                it != BackendTier.REMOTE_INFERENCE_ENDPOINT && it != BackendTier.REMOTE_PRIVATE_HF
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.analyze_backend_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    supportedTiers.forEachIndexed { idx, tier ->
                        SegmentedButton(
                            selected = uiState.selectedTier == tier,
                            onClick = { viewModel.setTier(tier) },
                            shape = SegmentedButtonDefaults.itemShape(idx, supportedTiers.size),
                            label = { Text(tier.displayName, maxLines = 1) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ParamSlider(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    minLabel: String,
    maxLabel: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
    }
}

