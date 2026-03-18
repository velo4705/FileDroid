package com.filedroid.ui.transfer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.filedroid.transfer.TransferDirection
import com.filedroid.transfer.TransferJob
import com.filedroid.transfer.TransferStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferQueueScreen(
    onNavigateBack: () -> Unit,
    viewModel: TransferViewModel = hiltViewModel()
) {
    val jobs by viewModel.jobs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transfers") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (jobs.any { it.status == TransferStatus.DONE || it.status == TransferStatus.CANCELLED }) {
                        TextButton(onClick = { viewModel.clearCompleted() }) { Text("Clear done") }
                    }
                }
            )
        }
    ) { padding ->
        if (jobs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No transfers yet.", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(jobs, key = { it.id }) { job ->
                    TransferJobRow(job = job, onCancel = { viewModel.cancel(job.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun TransferJobRow(job: TransferJob, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (job.direction == TransferDirection.UPLOAD)
                Icons.Default.Upload else Icons.Default.Download,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(job.fileName, style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            when (job.status) {
                TransferStatus.IN_PROGRESS -> {
                    LinearProgressIndicator(
                        progress = { job.progressFraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            append("${job.progressPercent}%")
                            if (job.speedBytesPerSec > 0) append("  •  ${formatSpeed(job.speedBytesPerSec)}")
                            if (job.etaSeconds > 0) append("  •  ETA ${formatEta(job.etaSeconds)}")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                TransferStatus.QUEUED ->
                    Text("Queued", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                TransferStatus.DONE ->
                    Text("Done", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                TransferStatus.FAILED ->
                    Text("Failed: ${job.errorMessage}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error, maxLines = 2)
                TransferStatus.CANCELLED ->
                    Text("Cancelled", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
            }
        }
        if (job.status == TransferStatus.IN_PROGRESS || job.status == TransferStatus.QUEUED) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun formatSpeed(bps: Long): String = when {
    bps < 1024 -> "$bps B/s"
    bps < 1024 * 1024 -> "${bps / 1024} KB/s"
    else -> "${bps / (1024 * 1024)} MB/s"
}

private fun formatEta(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
}
