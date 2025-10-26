package com.example.shoppingassistant

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.shoppingassistant.feature.chat.ChatScreen
import com.example.shoppingassistant.ui.navigation.EdgeGestureMenuV4
import com.example.shoppingassistant.ui.theme.AppTheme

@Composable
fun App() {
    val nav = rememberNavController()

    AppTheme {
        Scaffold { innerPadding ->
            EdgeGestureMenuV4(
                onNavigate = { route ->
                    when (route) {
                        "chat" -> nav.navigate("chat") { launchSingleTop = true; popUpTo("chat"){ inclusive = false } }
                        "myItems" -> nav.navigate("chat")
                        "subscriptions" -> nav.navigate("chat")
                        "profile" -> nav.navigate("chat")
                    }
                }
            ){
                NavHost(
                    navController = nav,
                    startDestination = "chat",
                    modifier = androidx.compose.ui.Modifier.padding(innerPadding)
                ) {
                    composable("chat") {
                        ChatScreen(
                            nav = nav,
                            modifier = Modifier
                                .wrapContentSize(Alignment.Center) // ← контент ровно по центру
                                .padding(16.dp)                     // немного воздуха
                        )
                    }

                }
            }
        }
    }
}
