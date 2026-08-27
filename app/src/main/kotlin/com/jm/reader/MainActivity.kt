package com.jm.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jm.reader.ui.JMRoot
import com.jm.reader.ui.theme.JMReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JMReaderTheme {
                JMRoot(JMApplication.from(this))
            }
        }
    }
}
