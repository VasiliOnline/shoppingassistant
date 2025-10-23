package com.example.shoppingassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.shoppingassistant.feature.chat.ChatScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            MaterialTheme {
                NavHost(navController, startDestination = "chat") {
                    composable("chat") { ChatScreen(navController) }
                    // composable("subscriptions") { SubscriptionsScreen(navController) }
                    //composable("analytics") { AnalyticsScreen(navController) }
                }
            }
        }
    }
}
