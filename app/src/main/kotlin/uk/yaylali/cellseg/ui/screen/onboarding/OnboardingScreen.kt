package uk.yaylali.cellseg.ui.screen.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import uk.yaylali.cellseg.R
import uk.yaylali.cellseg.domain.model.BackendTier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) onContinue()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            // ── Model download checkbox ────────────────────────────────────
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Checkbox(
                        checked = uiState.downloadModel,
                        onCheckedChange = { viewModel.setDownloadModel(it) },
                    )
                    Column {
                        Text(
                            stringResource(R.string.onboarding_download_model),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Required for on-device segmentation",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (uiState.isDownloading) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                        LinearProgressIndicator(
                            progress = { uiState.downloadProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${(uiState.downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                uiState.downloadError?.let { err ->
                    Text(
                        err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                    )
                }
            }

            // ── Backend tier picker ────────────────────────────────────────
            val tiers = listOf(BackendTier.LOCAL_CYTO3, BackendTier.REMOTE_PUBLIC_HF)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.settings_default_backend),
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

            // ── HF token (optional) ────────────────────────────────────────
            OutlinedTextField(
                value = uiState.hfToken,
                onValueChange = { viewModel.setHfToken(it) },
                label = { Text(stringResource(R.string.onboarding_hf_token_hint)) },
                placeholder = { Text("hf_…") },
                supportingText = { Text(stringResource(R.string.onboarding_hf_token_link)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.complete() },
                enabled = !uiState.isDownloading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isDownloading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.onboarding_get_started))
                }
            }
        }
    }
}

