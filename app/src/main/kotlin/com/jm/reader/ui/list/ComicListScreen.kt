package com.jm.reader.ui.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jm.reader.data.model.ComicListItem
import com.jm.reader.data.repo.RepoResult
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalRepository
import com.jm.reader.ui.components.AppTopBar
import com.jm.reader.ui.components.ComicGrid
import com.jm.reader.ui.components.ErrorView
import com.jm.reader.ui.components.LoadingView
import com.jm.reader.ui.nav.Routes
import kotlinx.coroutines.launch

/**
 * Generic paged comic list. `type`:
 *  - "category"     -> categories/filter with c = [id]
 *  - "promote"      -> promote_list with id = [id]
 *  - "serialization"-> serialization
 *  - anything else  -> latest
 */
@Composable
fun ComicListScreen(
    navController: NavHostController,
    type: String,
    title: String,
    id: String,
) {
    val repo = LocalRepository.current
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<ComicListItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(1) }
    var endReached by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }

    suspend fun loadInitial() {
        loading = true
        error = null
        page = 1
        val r = when (type) {
            "category" -> repo.categoriesFilter(id, "", 1)
            "promote" -> repo.getPromoteList(id, 1)
            "serialization" -> repo.getSerializationMore(null, null, 1)
            else -> repo.getLatest(1)
        }
        when (r) {
            is RepoResult.Ok -> {
                items = r.data
                endReached = r.data.isEmpty()
            }
            is RepoResult.Err -> error = r.message
        }
        loading = false
    }

    suspend fun loadMore() {
        if (loadingMore || endReached) return
        loadingMore = true
        val next = page + 1
        val r = when (type) {
            "category" -> repo.categoriesFilter(id, "", next)
            "promote" -> repo.getPromoteList(id, next)
            "serialization" -> repo.getSerializationMore(null, null, next)
            else -> repo.getLatest(next)
        }
        if (r is RepoResult.Ok) {
            items = (items + r.data).distinctBy { it.id }
            page = next
            endReached = r.data.isEmpty()
        }
        loadingMore = false
    }

    LaunchedEffect(type, id) { loadInitial() }

    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState, items.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                if (last != null && !loading && !loadingMore && last >= items.size - 8) {
                    scope.launch { loadMore() }
                }
            }
    }

    Scaffold(topBar = { AppTopBar(title.ifBlank { s.list }, onBack = { navController.popBackStack() }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading && items.isEmpty() -> LoadingView()
                error != null && items.isEmpty() -> ErrorView(error!!, onRetry = { scope.launch { loadInitial() } })
                else -> ComicGrid(
                    items = items,
                    repo = repo,
                    onItemClick = { navController.navigate(Routes.comicDetail(it.id)) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (loadingMore) {
                Box(Modifier.align(Alignment.BottomCenter).padding(8.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                }
            }
        }
    }
}
