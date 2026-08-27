package com.jm.reader.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jm.reader.data.model.ComicListItem
import com.jm.reader.data.model.TagItem
import com.jm.reader.data.repo.RepoResult
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalRepository
import com.jm.reader.ui.components.AppTopBar
import com.jm.reader.ui.components.ComicGrid
import com.jm.reader.ui.components.EmptyView
import com.jm.reader.ui.components.LoadingView
import com.jm.reader.ui.nav.Routes
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(navController: NavHostController, initialHotTagsOnly: Boolean = false) {
    val repo = LocalRepository.current
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var hotTags by remember { mutableStateOf<List<TagItem>>(emptyList()) }
    var random by remember { mutableStateOf<List<ComicListItem>>(emptyList()) }
    var results by remember { mutableStateOf<List<ComicListItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun loadDiscover() {
        when (val h = repo.hotTags()) { is RepoResult.Ok -> hotTags = h.data; is RepoResult.Err -> Unit }
        when (val rr = repo.randomRecommend()) { is RepoResult.Ok -> random = rr.data; is RepoResult.Err -> Unit }
    }

    suspend fun doSearch(kw: String) {
        if (kw.isBlank()) return
        submitted = kw
        loading = true
        error = null
        when (val r = repo.search(kw, null, 1)) {
            is RepoResult.Ok -> { results = r.data; loading = false }
            is RepoResult.Err -> { error = r.message; loading = false }
        }
    }

    LaunchedEffect(Unit) { loadDiscover() }

    Scaffold(topBar = { AppTopBar(s.search, onBack = { navController.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!initialHotTagsOnly) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(s.searchComicHint) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = s.clear) }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboard?.hide()
                        scope.launch { doSearch(query) }
                    }),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    loading -> LoadingView()
                    error != null && results.isEmpty() -> EmptyView(error!!, Modifier.fillMaxSize())
                    submitted.isNotBlank() -> {
                        if (results.isEmpty()) EmptyView(s.noResult, Modifier.fillMaxSize())
                        else ComicGrid(
                            items = results,
                            repo = repo,
                            onItemClick = { navController.navigate(Routes.comicDetail(it.id)) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        if (hotTags.isNotEmpty()) {
                            item {
                                Text(s.hotTagsTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp, 12.dp, 12.dp, 4.dp))
                            }
                            item {
                                FlowRow(
                                    Modifier.padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    hotTags.take(30).forEach { tag ->
                                        SuggestionChip(
                                            onClick = { query = tag.title; scope.launch { doSearch(tag.title) } },
                                            label = { Text("#${tag.title}", maxLines = 1) },
                                        )
                                    }
                                }
                            }
                        }
                        if (random.isNotEmpty()) {
                            item {
                                Text(s.forYou, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp, 16.dp, 12.dp, 4.dp))
                            }
                            items(random.take(24), key = { it.id }) { item ->
                                RandomRow(item, repo, onClick = { navController.navigate(Routes.comicDetail(item.id)) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RandomRow(
    item: ComicListItem,
    repo: com.jm.reader.data.repo.AppRepository,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val cover = if (item.image.isNotBlank()) item.image else repo.comicCover(item.id, item.updateAt)
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(cover).crossfade(true).build(),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp, 74.dp).clip(RoundedCornerShape(6.dp)),
        )
        Column(Modifier.weight(1f).padding(top = 4.dp)) {
            Text(item.name, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
            item.category?.title?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
