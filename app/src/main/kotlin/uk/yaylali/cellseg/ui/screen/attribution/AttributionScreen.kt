package uk.yaylali.cellseg.ui.screen.attribution

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uk.yaylali.cellseg.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttributionScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.attribution_title)) },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.attribution_title), style = MaterialTheme.typography.titleLarge)

            AttributionEntry(
                name = "Cellpose",
                description = "Stringer et al. (2021). Cellpose: a generalist algorithm for cellular segmentation. Nature Methods.",
                licence = "BSD 3-Clause",
            )
            AttributionEntry(
                name = "ONNX Runtime",
                description = "Microsoft. Open Neural Network Exchange Runtime.",
                licence = "MIT",
            )
            AttributionEntry(
                name = "Jetpack Compose",
                description = "Google. Modern toolkit for building native Android UI.",
                licence = "Apache 2.0",
            )
            AttributionEntry(
                name = "Coil",
                description = "coil-kt. Image loading for Android backed by Kotlin Coroutines.",
                licence = "Apache 2.0",
            )
            AttributionEntry(
                name = "Hilt",
                description = "Google. Dependency injection library for Android.",
                licence = "Apache 2.0",
            )
            AttributionEntry(
                name = "Room",
                description = "Google. SQLite abstraction library for Android.",
                licence = "Apache 2.0",
            )
            AttributionEntry(
                name = "OkHttp",
                description = "Square. An efficient HTTP client for Android and Java.",
                licence = "Apache 2.0",
            )
            AttributionEntry(
                name = "Retrofit",
                description = "Square. Type-safe HTTP client for Android.",
                licence = "Apache 2.0",
            )
            AttributionEntry(
                name = "Timber",
                description = "Jake Wharton. A logger with a small, extensible API.",
                licence = "Apache 2.0",
            )

            HorizontalDivider()
            Text(
                stringResource(R.string.attribution_cellpose_cc_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AttributionEntry(name: String, description: String, licence: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium)
        Text("Licence: $licence", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
