package com.example.samsungremotetvandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.samsungremotetvandroid.core.design.SamsungRemoteTheme
import com.example.samsungremotetvandroid.presentation.app.SamsungRemoteApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SamsungRemoteTheme {
                SamsungRemoteApp()
            }
        }
    }
}
