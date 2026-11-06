package com.jarvis.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.jarvis.ui.JarvisRoot
import com.jarvis.ui.JarvisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JarvisTheme {
                val vm = remember { applicationContext.graph.controller }
                JarvisRoot(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // App is visible: the only moment Android lets the service (re)start with microphone access.
        Power.ensureService(this, mic = true)
    }
}
