package com.jm.reader.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jm.reader.data.download.DownloadManager
import com.jm.reader.data.model.ComicListItem
import com.jm.reader.data.repo.RepoResult
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalDownloadManager
import com.jm.reader.ui.LocalRepository
import com.jm.reader.ui.LocalSession
import com.jm.reader.ui.components.ComicGrid
import com.jm.reader.ui.components.EmptyView
import com.jm.reader.ui.components.LoadingView
import com.jm.reader.ui.nav.Routes
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    val repo = LocalRepository.current
    val session = LocalSession.current
    val downloadManager = LocalDownloadManager.current
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    s.library,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 0.dp),
                )
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(s.favorites) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(s.history) })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(s.downloads) })
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (tab < 2 && session.jwtToken.isNullOrBlank()) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                    Text(s.libraryLoginRequired, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { navController.navigate(Routes.LOGIN) }, modifier = Modifier.padding(top = 12.dp)) {
                        Text(s.goLogin)
                    }
                }
            } else {
                when (tab) {
                    0 -> FavoritesList(navController, repo, s)
                    1 -> HistoryList(navController, repo, s)
                    2 -> DownloadsList(navController, downloadManager, s, scope)
                }
            }
        }
    }
}

@Composable
private fun FavoritesList(
    navController: NavHostController,
    repo: com.jm.reader.data.repo.AppRepository,
    s: com.jm.reader.ui.strings.AppStrings,
) {
    var items by remember { mutableStateOf<List<ComicListItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        loading = true
        when (val r = repo.favorites(1)) {
            is RepoResult.Ok -> { items = r.data; error = null }
            is RepoResult.Err -> error = r.message
        }
        loading = false
    }

    Box(Modifier.fillMaxSize()) {
        when {
            loading -> LoadingView()
            error != null -> EmptyView(error!!, Modifier.fillMaxSize())
            items.isEmpty() -> EmptyView(s.emptyFavorites, Modifier.fillMaxSize())
            else -> ComicGrid(
                items = items,
                repo = repo,
                onItemClick = { navController.navigate(Routes.comicDetail(it.id)) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun HistoryList(
    navController: NavHostController,
    repo: com.jm.reader.data.repo.AppRepository,
    s: com.jm.reader.ui.strings.AppStrings,
) {
    var items by remember { mutableStateOf<List<ComicListItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        loading = true
        when (val r = repo.watchList(1)) {
            is RepoResult.Ok -> {
                items = r.data.optJSONArray("list")?.let { ja ->
                    (0 until ja.length()).mapNotNull { ja.optJSONObject(it)?.let { ComicListItem.fromJson(it) } }
                } ?: emptyList()
                error = null
            }
            is RepoResult.Err -> error = r.message
        }
        loading = false
    }

    Box(Modifier.fillMaxSize()) {
        when {
            loading -> LoadingView()
            error != null -> EmptyView(error!!, Modifier.fillMaxSize())
            items.isEmpty() -> EmptyView(s.emptyHistory, Modifier.fillMaxSize())
            else -> ComicGrid(
                items = items,
                repo = repo,
                onItemClick = { navController.navigate(Routes.comicDetail(it.id)) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun DownloadsList(
    navController: NavHostController,
    downloadManager: DownloadManager,
    s: com.jm.reader.ui.strings.AppStrings,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val albums by downloadManager.albums.collectAsState()
    val downloading by downloadManager.downloading.collectAsState()
    var deleteTarget by remember { mutableStateOf<DownloadManager.DownloadedAlbum?>(null) }

    Box(Modifier.fillMaxSize()) {
        if (albums.isEmpty()) {
            EmptyView(s.emptyDownloads, Modifier.fillMaxSize())
        } else {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
                items(albums.sortedByDescending { it.timestamp }, key = { it.albumId }) { album ->
                    val prog = downloading[album.albumId]
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            navController.navigate(Routes.offlineReader(album.albumId))
                        }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(album.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (prog?.phase == "downloading") {
                                    s.downloadProgressFmt.format(prog.current, prog.total)
                                } else {
                                    "${album.pageCount} P  ·  ${s.downloaded}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            Text(s.offlineRead, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { deleteTarget = album }) {
                            Icon(Icons.Filled.Delete, contentDescription = s.delete, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { album ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(s.delete) },
            text = { Text(s.deleteConfirmFmt.format(album.name)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { downloadManager.deleteAlbum(album.albumId) }
                    deleteTarget = null
                }) { Text(s.confirm) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(s.cancel) }
            },
        )
    }
}
