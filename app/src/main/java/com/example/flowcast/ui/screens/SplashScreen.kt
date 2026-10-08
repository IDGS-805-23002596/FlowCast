package com.example.flowcast.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import com.example.flowcast.R
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowcast.ui.theme.secundaryColor
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateToNext: () -> Unit
){
    LaunchedEffect(key1 = true) {
        delay(2500)
        onNavigateToNext()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_logo_estandar),
                contentDescription = "Logo de la App",
                modifier = Modifier
                    .size(180.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            CircularProgressIndicator(
                color = secundaryColor,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
        }


    }
}