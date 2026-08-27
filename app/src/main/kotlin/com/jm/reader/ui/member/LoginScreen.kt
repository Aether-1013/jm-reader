package com.jm.reader.ui.member

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jm.reader.data.repo.RepoResult
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalRepository
import com.jm.reader.ui.components.AppTopBar
import com.jm.reader.ui.nav.Routes
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(navController: NavHostController) {
    val repo = LocalRepository.current
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { AppTopBar(s.login, onBack = { navController.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(s.username) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(s.password) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            error?.let {
                Text(it, color = androidx.compose.ui.graphics.Color(0xFFE53935), style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
            Button(
                onClick = {
                    if (username.isBlank() || password.isBlank()) { error = s.enterAccountPassword; return@Button }
                    loading = true
                    error = null
                    scope.launch {
                        when (val r = repo.login(username, password)) {
                            is RepoResult.Ok -> navController.popBackStack()
                            is RepoResult.Err -> { error = r.message; loading = false }
                        }
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(s.login)
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                TextButton(onClick = { navController.navigate(Routes.REGISTER) }) { Text("${s.register} →") }
            }
        }
    }
}
