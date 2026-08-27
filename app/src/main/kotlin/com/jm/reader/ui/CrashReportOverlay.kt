package com.jm.reader.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.jm.reader.util.CrashReportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shows a dialog on the next launch after a crash, letting the user read and copy
 * the saved crash report. Once dismissed (or copied) the stored report is cleared.
 */
@Composable
fun CrashReportOverlay(context: Context) {
    val s = LocalAppStrings.current
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        text = withContext(Dispatchers.IO) { CrashReportManager.readCrash(context) }
    }
    val current = text ?: return
    val clipboard = LocalClipboardManager.current

    fun dismiss() {
        CrashReportManager.clearCrash(context)
        text = null
    }

    AlertDialog(
        onDismissRequest = { dismiss() },
        title = { Text(s.crashTitle) },
        text = {
            Column {
                Text(
                    s.crashHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .heightIn(max = 320.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        current,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                @Suppress("DEPRECATION")
                clipboard.setText(AnnotatedString(current))
                dismiss()
            }) { Text(s.copyLog) }
        },
        dismissButton = {
            TextButton(onClick = { dismiss() }) { Text(s.close) }
        },
    )
}
