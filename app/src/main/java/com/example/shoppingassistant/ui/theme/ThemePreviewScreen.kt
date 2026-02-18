package com.example.shoppingassistant.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePreviewScreen(
    darkTheme: Boolean = false,
) {
    val colorScheme = if (darkTheme) {
        // ТЁМНАЯ ТЕМА
        darkColorScheme(
            primary = Color(0xFFFFB020),             // тёплый акцент (можно под золото)
            background = Color(0xFF0B0B10),          // общий фон страницы
            surface = Color(0xFF18181F),             // фон карточек / панелей
            onBackground = Color(0xFFF9FAFB),        // текст на фоне
            onSurface = Color(0xFFF9FAFB)
        )
    } else {
        // СВЕТЛАЯ ТЕМА
        lightColorScheme(
            primary = Color(0xFFFF8A00),             // акцент (оранж/золото)
            background = Color(0xFFF4F4F5),          // мягкий серо-белый фон страницы
            surface = Color.White,                   // фон карточек
            onBackground = Color(0xFF111827),        // тёмный текст
            onSurface = Color(0xFF111827)
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "ShoppingAssistant",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = true,
                        onClick = { },
                        label = { Text("Поиск") },
                        icon = { /* иконка потом */ }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { },
                        label = { Text("Мои товары") },
                        icon = { }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { },
                        label = { Text("Профиль") },
                        icon = { }
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Поле поиска (упрощённый мок)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "Найти товар, бренд или категорию",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Text(
                    text = "Лучшие предложения",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Три карточки топ-офферов
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OfferCard(
                        title = "Лучший по цене",
                        subtitle = "Seller_name в name_of_shop",
                        price = "12 490 ₽",
                        days = "Доставка: 2–3 дня",
                        badge = "Ниже рынка ~18%"
                    )
                    OfferCard(
                        title = "Самая быстрая доставка",
                        subtitle = "Seller_name в name_of_shop",
                        price = "13 100 ₽",
                        days = "Доставка: завтра",
                        badge = "Быстрее, чем 92% офферов"
                    )
                    OfferCard(
                        title = "Самый надёжный продавец",
                        subtitle = "Seller_name в name_of_shop",
                        price = "12 990 ₽",
                        days = "Доставка: 3–5 дней",
                        badge = "Рейтинг продавца 4.9"
                    )
                }
            }
        }
    }
}

@Composable
private fun OfferCard(
    title: String,
    subtitle: String,
    price: String,
    days: String,
    badge: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
            Text(
                text = "Цена: $price",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = days,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AssistChip(
                    onClick = { /* Аналитика */ },
                    label = { Text(badge) }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { /* Подробнее */ }) {
                        Text("Подробнее")
                    }
                    TextButton(onClick = { /* Аналитика */ }) {
                        Text("Аналитика")
                    }
                }
            }
        }
    }
}

@Preview(
    name = "Light theme",
    showBackground = true,
    widthDp = 360,
    heightDp = 720
)
@Composable
fun LightThemePreview() {
    ThemePreviewScreen(darkTheme = false)
}

@Preview(
    name = "Dark theme",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    widthDp = 360,
    heightDp = 720
)
@Composable
fun DarkThemePreview() {
    ThemePreviewScreen(darkTheme = true)
}
