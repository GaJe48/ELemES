package com.gaje48.elemes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun MeetingDetail(
    viewModel: Backend,
    meetingUrl: String,
    onBackClick: () -> Unit,
) {
    val detail = viewModel.meetingDetailUI
    val isLoading = viewModel.isLoading
    val errorMessage = viewModel.errorMessage
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val hazeState = rememberHazeState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state = rememberPullToRefreshState()

    LaunchedEffect(Unit) {
        viewModel.getMeetingDetail(meetingUrl)
        viewModel.executePresence(meetingUrl)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        )

        PullToRefreshBox(
            isRefreshing = viewModel.isRefreshing,
            state = state,
            onRefresh = { viewModel.getMeetingDetail(meetingUrl, isSwipe = true) },
            contentAlignment = Alignment.TopCenter,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = state,
                    isRefreshing = viewModel.isRefreshing,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        ) {
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                containerColor = Color.Transparent,
                topBar = {
                    LargeTopAppBar(
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.hazeEffect(
                            state = hazeState,
                            style = HazeMaterials.ultraThin()
                        ),
                        title = {
                            Text(
                                "Detail Pertemuan",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier
                                    .padding(start = 8.dp, end = 8.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                }
            ) { paddingValues ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding() + 16.dp,
                        bottom = 24.dp,
                        start = 20.dp,
                        end = 20.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (detail.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    isLoading -> LoadingGif()
                                    errorMessage.isNotEmpty() -> ErrorGif(
                                        message = errorMessage,
                                        onRetry = { viewModel.getMeetingDetail(meetingUrl) }
                                    )
                                    else -> EmptyGif(label = "Tidak ada file materi yang tersedia")
                                }
                            }
                        }
                    } else {
                        val fileKeywords = listOf("pdf", "word", "powerpoint", "excel", "archive")

                        val (files, links) = detail.partition { item ->
                            fileKeywords.any { keyword ->
                                item.type.contains(keyword, ignoreCase = true)
                            }
                        }

                        if (files.isNotEmpty()) {
                            item {
                                SectionHeader(icon = Icons.Default.Description, label = "File Materi")
                            }
                            items(files) { item ->
                                ExpressiveResourceCard(
                                    title = item.desc,
                                    subtitle = "Ketuk untuk mengunduh dokumen",
                                    icon = iconPainter(item.type),
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    onClick = { viewModel.executeDownload(context, item.url) }
                                )
                            }
                        }

                        if (links.isNotEmpty()) {
                            item {
                                SectionHeader(icon = Icons.Default.Link, label = "Tautan Lainnya")
                            }
                            items(links) { item ->
                                ExpressiveResourceCard(
                                    title = item.desc,
                                    subtitle = "Ketuk untuk membuka tautan",
                                    icon = iconPainter(item.type),
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                    onClick = { uriHandler.openUri(item.url) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(icon: ImageVector, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ExpressiveResourceCard(
    title: String,
    subtitle: String,
    icon: Painter,
    containerColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun iconPainter(type: String): Painter {
    return when {
        type.contains("pdf") -> painterResource(id = R.drawable.pdf)
        type.contains("powerpoint") -> painterResource(id = R.drawable.powerpoint)
        type.contains("picture") -> painterResource(id = R.drawable.image)
        type.contains("video") -> painterResource(id = R.drawable.video)
        else -> painterResource(id = R.drawable.link)
    }
}