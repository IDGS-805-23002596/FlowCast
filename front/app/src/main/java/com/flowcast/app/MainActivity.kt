package com.flowcast.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.flowcast.app.ui.navigation.NavegacionFlowCast
import com.flowcast.app.ui.theme.TemaFlowCast

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TemaFlowCast {
                NavegacionFlowCast(application as AplicacionFlowCast)
            }
        }
    }
}
