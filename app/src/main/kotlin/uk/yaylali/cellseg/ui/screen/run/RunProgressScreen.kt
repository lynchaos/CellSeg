package uk.yaylali.cellseg.ui.screen.run

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import uk.yaylali.cellseg.R
import uk.yaylali.cellseg.domain.model.RunStatus

@Composable
fun RunProgressScreen(
    runId: String,
    onCompleted: (runId: String) -> Unit,
    onError: () -> Unit,
    viewModel: RunProgressViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(runId) { viewModel.startRun(runId) }

    LaunchedEffect(uiState.status) {
        when (uiState.status) {
            RunStatus.COMPLETED -> onCompleted(runId)
            // CANCELLED: user tapped Give Up — navigate back immediately
            RunStatus.CANCELLED -> onError()
            // FAILED: stay on screen so user can read the error; back gesture exits
            else -> Unit
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Progress ring — indeterminate while waking up, determinate otherwise
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (uiState.isWakingUp) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(108.dp),
                        strokeWidth = 8.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { uiState.progress },
                        modifier = Modifier.size(108.dp),
                        strokeWidth = 8.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                    if (uiState.progress > 0f && uiState.status != RunStatus.FAILED) {
                        Text(
                            text = "${(uiState.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = uiState.statusMessage,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            when {
                uiState.isWakingUp -> {
                    Text(
                        text = stringResource(R.string.run_waking_retry_in, uiState.retryCountdown),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { viewModel.retryNow() }) {
                            Text(stringResource(R.string.run_retry_now))
                        }
                        TextButton(onClick = { viewModel.giveUp() }) {
                            Text(
                                stringResource(R.string.run_give_up),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                uiState.error != null -> {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = onError) {
                        Text(stringResource(R.string.run_give_up))
                    }
                }
                else -> {
                    // Normal in-progress subtitle
                    Text(
                        text = stringResource(R.string.run_progress_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
