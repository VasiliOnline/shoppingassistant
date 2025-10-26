package com.example.shoppingassistant

import android.annotation.SuppressLint
import android.app.Application
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.ImageLoader
import com.example.shoppingassistant.core.di.coreModule
import com.example.shoppingassistant.feature.chat.ChatScreen
import com.example.shoppingassistant.feature.chat.di.chatModule
import com.example.shoppingassistant.ui.navigation.EdgeGestureMenuV4
import com.example.shoppingassistant.ui.theme.AppTheme
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import com.example.shoppingassistant.core.ui.AppImageLoader

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@App)
            modules(
                coreModule,
                chatModule
            )
        }
    }
}
// где-нибудь рядом в этом же файле (или в ui/theme), если ещё нет
@SuppressLint("CompositionLocalNaming")
val AppImageLoader = staticCompositionLocalOf<ImageLoader> {
    error("ImageLoader not provided")
}


@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val context = LocalContext.current

    val imageLoader = remember {
        coil.ImageLoader.Builder(context)
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }

    AppTheme {
        CompositionLocalProvider(AppImageLoader provides imageLoader) {
            Scaffold { innerPadding ->
                EdgeGestureMenuV4(
                    onNavigate = { route ->
                        when (route) {
                            "chat" -> nav.navigate("chat") {
                                launchSingleTop = true
                                popUpTo("chat") { inclusive = false }
                            }
                            "myItems", "subscriptions", "profile" -> nav.navigate("chat")
                        }
                    }
                ) {
                    NavHost(
                        navController = nav,
                        startDestination = "chat",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("chat") {
                            ChatScreen(
                                nav = nav,
                                modifier = Modifier
                                    .wrapContentSize(Alignment.Center)
                                    .padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

