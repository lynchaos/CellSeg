package uk.yaylali.cellseg.ui.screen.sample

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import uk.yaylali.cellseg.R
import uk.yaylali.cellseg.domain.model.ImagingChannel
import uk.yaylali.cellseg.domain.model.Magnification
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleContextScreen(
    sampleId: String?,
    imageUri: String = "",
    onContinue: (sampleId: String, imageUri: String) -> Unit,
    onBack: () -> Unit,
    viewModel: SampleContextViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(sampleId) {
        sampleId?.let { viewModel.load(it, imageUri) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sample_context_title)) },
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
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // Image preview
            if (imageUri.isNotBlank()) {
                AsyncImage(
                    model = File(imageUri),
                    contentDescription = stringResource(R.string.run_detail_outline_cd),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }

            // Channel
            SectionCard(title = stringResource(R.string.sample_channel_label)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ImagingChannel.entries.forEachIndexed { idx, ch ->
                        SegmentedButton(
                            selected = uiState.channel == ch,
                            onClick = { viewModel.setChannel(ch) },
                            shape = SegmentedButtonDefaults.itemShape(idx, ImagingChannel.entries.size),
                            label = { Text(ch.displayName, maxLines = 1) },
                        )
                    }
                }
            }

            // Magnification
            SectionCard(title = stringResource(R.string.sample_magnification_label)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    Magnification.entries.forEachIndexed { idx, mag ->
                        SegmentedButton(
                            selected = uiState.magnification == mag,
                            onClick = { viewModel.setMagnification(mag) },
                            shape = SegmentedButtonDefaults.itemShape(idx, Magnification.entries.size),
                            label = { Text(mag.label, maxLines = 1) },
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    viewModel.save {
                        onContinue(it.sampleId, it.imageUri)
                    }
                },
                enabled = uiState.canContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                Text(stringResource(R.string.action_continue))
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}
