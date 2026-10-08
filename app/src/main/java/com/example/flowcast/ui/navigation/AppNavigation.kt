package com.example.flowcast.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.flowcast.FlowCastApp
import com.example.flowcast.ui.screens.SplashScreen

@Composable
fun AppNavigation(){
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination ="splash"
    ){
        composable("splash") {
            SplashScreen(
                onNavigateToNext = {
                    navController.navigate("main") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }

        composable ("main"){
            FlowCastApp()
        }
    }
}
