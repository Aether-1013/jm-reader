package com.jm.reader.ui.movies

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jm.reader.data.repo.RepoResult
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalRepository
import com.jm.reader.ui.components.AppTopBar
import com.jm.reader.ui.components.ErrorView
import com.jm.reader.ui.components.LoadingView
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun MovieDetailScreen(navController: NavHostController, id: String) {
    val repo = LocalRepository.current
    val s = LocalAppStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(id, reload) {
        loading = true
        when (val r = repo.movieInfo(id)) {
            is RepoResult.Ok -> { info = r.data; error = null }
            is RepoResult.Err -> error = r.message
        }
        loading = false
    }

    Scaffold(topBar = { AppTopBar(s.movieDetail, onBack = { navController.popBackStack() }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> LoadingView()
                error != null -> ErrorView(error!!, onRetry = { reload++ })
                info == null -> ErrorView(s.loadFail, onRetry = { reload++ })
                else -> {
                    val video = info!!.optJSONObject("video") ?: info!!
                    val title = video.optString("title")
                    val photo = video.optString("photo")
                    val fullUrl = video.optString("full_url")
                    val videoSrc = video.optString("video_src")

                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    ) {
                        if (photo.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(repo.imgUrl(photo)).crossfade(true).build(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                        }
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                        video.optString("description").let { if (it.isNotBlank()) Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }

                        if (fullUrl.isNotBlank()) {
                            Button(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            ) { Text(s.openInBrowser) }
                        } else if (videoSrc.isNotBlank()) {
                            Text("${s.videoAddress}：$videoSrc", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
        }
    }
}
