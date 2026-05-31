package com.sorrowblue.kpdfium.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sorrowblue.kpdfium.sample.data.setupCoil

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupCoil()
        AppContext.context = applicationContext
        enableEdgeToEdge()
        setContent {
            App()
        }
    }
}
