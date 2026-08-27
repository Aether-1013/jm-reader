package com.jm.reader.ui.daily

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jm.reader.data.model.ComicListItem
import com.jm.reader.data.repo.RepoResult
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalRepository
import com.jm.reader.ui.LocalSession
import com.jm.reader.ui.components.AppTopBar
import com.jm.reader.ui.components.ComicGrid
import com.jm.reader.ui.components.EmptyView
import com.jm.reader.ui.components.ErrorView
import com.jm.reader.ui.components.LoadingView
import com.jm.reader.ui.nav.Routes
import kotlinx.coroutines.launch

@Composable
fun DailyScreen(navController: NavHostController) {
    val repo = LocalRepository.current
    val session = LocalSession.current
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()

    var dailyId by remember { mutableStateOf("") }
    var eventName by remember { mutableStateOf("") }
    var signedCount by remember { mutableStateOf(0) }
    var items by remember { mutableStateOf<List<ComicListItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        val uid = session.memberJson?.let { runCatching { org.json.JSONObject(it).optString("uid") }.getOrNull() }
        if (uid.isNullOrBlank()) { error = s.loginFirst; loading = false; return }
        loading = true
        when (val r = repo.getDaily(uid)) {
            is RepoResult.Ok -> {
                dailyId = r.data.optString("daily_id")
                eventName = r.data.optString("event_name")
                val record = r.data.optJSONArray("record")
                var signed = 0
                if (record != null) {
                    for (i in 0 until record.length()) {
                        val week = record.optJSONArray(i) ?: continue
                        for (j in 0 until week.length()) {
                            week.optJSONObject(j)?.let { if (it.optBoolean("signed")) signed++ }
                        }
                    }
                }
                signedCount = signed
                error = null
            }
            is RepoResult.Err -> error = r.message
        }
        when (val r = repo.getDailyList(uid)) {
            is RepoResult.Ok -> {
                items = r.data.optJSONArray("list")?.let { ja ->
                    (0 until ja.length()).mapNotNull { ja.optJSONObject(it)?.let { ComicListItem.fromJson(it) } }
                } ?: emptyList()
            }
            is RepoResult.Err -> Unit
        }
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(topBar = { AppTopBar(s.checkIn, onBack = { navController.popBackStack() }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> LoadingView()
                error != null -> ErrorView(error!!, onRetry = { scope.launch { load() } })
                else -> Column(Modifier.fillMaxSize()) {
                    Card(Modifier.fillMaxWidth().padding(12.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(eventName.ifBlank { s.checkIn }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(s.signedDaysFmt.format(signedCount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                            msg?.let {
                                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                            }
                            val uid = session.memberJson?.let { runCatching { org.json.JSONObject(it).optString("uid") }.getOrNull() }
                            Button(
                                onClick = {
                                    if (dailyId.isBlank()) return@Button
                                    scope.launch {
                                        when (val r = repo.dailyCheck(uid ?: "", dailyId)) {
                                            is RepoResult.Ok -> msg = r.data.optString("msg").ifBlank { s.signIn + " ✓" }
                                            is RepoResult.Err -> msg = r.message
                                        }
                                        load()
                                    }
                                },
                                modifier = Modifier.padding(top = 8.dp),
                            ) { Text(s.signIn) }
                        }
                    }
                    if (items.isEmpty()) EmptyView(s.todayNoWork, Modifier.fillMaxSize())
                    else ComicGrid(
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
