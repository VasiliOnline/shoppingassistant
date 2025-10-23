package com.example.shoppingassistant

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.shoppingassistant.feature.chat.ChatScreen
import com.example.shoppingassistant.ui.theme.AppTheme

/**
 * App — корневой «каркас приложения»:
 * - включает тему (AppTheme)
 * - создаёт контроллер навигации (rememberNavController — «пульт переходов»)
 * - описывает маршруты (NavHost — «карта экранов»)
 */
@Composable
fun App() {
    val nav = rememberNavController()

    AppTheme {
        // Scaffold — каркас страницы (место под шапку/контент/панели, если понадобятся)
        Scaffold { paddingValues ->
            NavHost(
                    navController = nav,
                    startDestination = "chat", // стартовый экран — чат
                    modifier = Modifier.padding(paddingValues) // ← используем padding
                ) {
                    composable("chat") { ChatScreen(nav) }

                    // позже добавим:
                    // composable("myItems")        { ... } // Мои товары (зеркала)
                    // composable("subscriptions")  { ... } // Подписки
                    // composable("profile")        { ... } // Профиль
                    // composable("details/{id}")   { ... } // Подробнее о товаре
                }
            }
        }
    }

