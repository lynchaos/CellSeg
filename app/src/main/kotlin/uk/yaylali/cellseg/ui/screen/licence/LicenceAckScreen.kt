package uk.yaylali.cellseg.ui.screen.licence

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import uk.yaylali.cellseg.R

@Composable
fun LicenceAckScreen(
    onAcknowledged: () -> Unit,
    viewModel: LicenceAckViewModel = hiltViewModel(),
) {
    LicenceAckScreenContent(
        onAcknowledged = onAcknowledged,
        onAcknowledge = { viewModel.acknowledge() },
    )
}

@Composable
fun LicenceAckScreenContent(
    onAcknowledged: () -> Unit,
    onAcknowledge: () -> Unit,
) {
    val scrollState = rememberScrollState()
    var checked by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { checked = it },
                            modifier = Modifier.testTag("ack_checkbox"),
                        )
                        Text(
                            text = stringResource(R.string.licence_checkbox_label),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onAcknowledge()
                            onAcknowledged()
                        },
                        enabled = checked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ack_button"),
                    ) {
                        Text(stringResource(R.string.licence_ack_button))
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(scrollState),
        ) {
            Text(
                text = stringResource(R.string.licence_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.licence_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
