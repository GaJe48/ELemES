package com.example.lmsunindra

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingDetail(
    viewModel: Backend,
    meetingUrl: String,
    onBackClick: () -> Unit,
    onTaskClick: (String) -> Unit
) {
    val detail = viewModel.meetingDetailUI
    val isLoading = viewModel.isLoading
    val context = LocalContext.current

    LaunchedEffect(meetingUrl) {
        viewModel.getMeetingDetail(meetingUrl)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detail Pertemuan", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading && detail.meetingFileList.isEmpty() && detail.gMeetUrl.isEmpty() && detail.taskUrl.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        "Materi & Sumber Daya",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                if (detail.meetingFileList.isNotEmpty()) {
                    itemsIndexed(detail.meetingFileList) { index, fileUrl ->
                        ResourceCard(
                            title = "Materi Pertemuan ${index + 1}",
                            subtitle = "Dokumen Pendukung",
                            icon = Icons.Default.Description,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { viewModel.downloadMateri(context, fileUrl) }
                        )
                    }
                } else if (!isLoading) {
                    item {
                        Text(
                            "Tidak ada file materi yang tersedia.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                if (detail.gMeetUrl.isNotEmpty() || detail.taskUrl.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Tautan Penting",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                if (detail.gMeetUrl.isNotEmpty()) {
                    item {
                        ResourceCard(
                            title = "Google Meet",
                            subtitle = "Klik untuk bergabung ke kelas online",
                            icon = Icons.Default.VideoCall,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            onClick = { /* Handle GMeet */ }
                        )
                    }
                }

                if (detail.taskUrl.isNotEmpty()) {
                    item {
                        ResourceCard(
                            title = "Halaman Tugas",
                            subtitle = "Kumpulkan tugas sebelum tenggat waktu",
                            icon = Icons.Default.Warning,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            onClick = { onTaskClick(detail.taskUrl) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ResourceCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
