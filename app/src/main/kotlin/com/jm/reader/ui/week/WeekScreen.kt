package com.jm.reader.ui.week

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import org.json.JSONObject

private data class WeekOption(val id: String, val title: String)

@Composable
fun WeekScreen(navController: NavHostController) {
    val repo = LocalRepository.current
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<WeekOption>>(emptyList()) }
    var types by remember { mutableStateOf<List<WeekOption>>(emptyList()) }
    var selectedCat by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<ComicListItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun loadFilter(cat: String, type: String) {
        loading = true
        when (val r = repo.getWeekFilter(cat, type)) {
            is RepoResult.Ok -> { items = r.data; loading = false; error = null }
            is RepoResult.Err -> { error = r.message; loading = false }
        }
    }

    suspend fun loadWeek() {
        loading = true
        when (val r = repo.getWeek()) {
            is RepoResult.Ok -> {
                val cats = r.data.optJSONArray("categories")?.let { ja ->
                    (0 until ja.length()).mapNotNull { ja.optJSONObject(it)?.let { WeekOption(it.optString("id"), it.optString("title")) } }
                } ?: emptyList()
                val typs = r.data.optJSONArray("type")?.let { ja ->
                    (0 until ja.length()).mapNotNull { ja.optJSONObject(it)?.let { WeekOption(it.optString("id"), it.optString("title")) } }
                } ?: emptyList()
                categories = cats
                types = typs
                selectedCat = cats.firstOrNull()?.id ?: ""
                selectedType = typs.getOrNull(2)?.id ?: typs.firstOrNull()?.id ?: ""
                error = null
                loadFilter(selectedCat, selectedType)
            }
            is RepoResult.Err -> { error = r.message; loading = false }
        }
    }

    LaunchedEffect(Unit) { loadWeek() }

    Scaffold(topBar = { AppTopBar(s.weekRanking, onBack = { navController.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCat == cat.id,
                        onClick = {
                            selectedCat = cat.id
                            scope.launch { loadFilter(cat.id, selectedType) }
                        },
                        label = { Text(cat.title, maxLines = 1) },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                types.forEach { t ->
                    FilterChip(
                        selected = selectedType == t.id,
                        onClick = {
                            selectedType = t.id
                            scope.launch { loadFilter(selectedCat, t.id) }
                        },
                        label = { Text(t.title, maxLines = 1) },
                    )
                }
            }
            Box(Modifier.fillMaxSize()) {
                when {
                    loading -> LoadingView()
                    error != null -> ErrorView(error!!, onRetry = { scope.launch { loadFilter(selectedCat, selectedType) } })
                    else -> ComicGrid(
                        items = items,
                        repo = repo,
                        onItemClick = { navController.navigate(Routes.comicDetail(it.id)) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
