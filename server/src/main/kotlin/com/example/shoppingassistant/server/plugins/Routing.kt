// Last synced: 2025-12-10 15:35:15
package com.example.shoppingassistant.server.plugins

import com.example.shoppingassistant.server.auth.SessionStore
import com.example.shoppingassistant.server.auth.authRoutes
import com.example.shoppingassistant.server.config.RedisConfig
import com.example.shoppingassistant.server.offers.offerRoutes
import com.example.shoppingassistant.server.offers.offerAlertsRoutes
import com.example.shoppingassistant.server.offers.offerAnalyticsRoutes
import com.example.shoppingassistant.server.offers.offerTrackingRoutes
import com.example.shoppingassistant.server.profile.profileRoutes
import com.example.shoppingassistant.server.push.pushRoutes
import com.example.shoppingassistant.server.storage.storageRoutes
import com.example.shoppingassistant.server.subscriptions.subscriptionsRoutes
import com.example.shoppingassistant.server.tracks.tracksRoutes
import com.example.shoppingassistant.server.ugc.ugcRoutes
import com.example.shoppingassistant.server.useroffers.userOffersRoutes
import com.example.shoppingassistant.server.useroffers.actions.userOffersActionsRoutes
import com.example.shoppingassistant.server.useroffers.price.userOffersPriceRoutes
import com.example.shoppingassistant.server.vision.visionRoutes
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.time.Instant
import kotlinx.serialization.Serializable
import org.koin.java.KoinJavaComponent

/**
 * Базовый routing backend-сервиса:
 * - /health — health-check;
 * - /api/meta — технический meta-эндпойнт;
 * - /api/auth — корень фичи "Авторизация";
 * - /api/profile — корень фичи "Профиль" (паттерн root+manifest+state+tasks);
 * - /api/offers* — поиск и аналитика предложений;
 * - /api/storage* — загрузка/хранение фото;
 * - /api/vision* — vision-нормализация (камера);
 * - /api/ugc* — UGC-зеркало по ссылке.
 */
@Serializable
data class RedisMetaInfo(
    val enabled: Boolean,
    val urlPresent: Boolean,
    val sessionTtlSeconds: Long,
    val storeType: String,
)

@Serializable
data class MetaResponse(
    val service: String,
    val version: String,
    val time: String,
    val redis: RedisMetaInfo,
)

fun Application.configureRouting() {
    routing {
        // Простой health-check
        get("/health") {
            call.respondText("OK")
        }

        // Технический meta-эндпойнт + информация о Redis/SessionStore
        get("/api/meta") {
            val redisConfig: RedisConfig? = try {
                KoinJavaComponent.get(RedisConfig::class.java)
            } catch (_: Exception) {
                null
            }

            val sessionStore: SessionStore? = try {
                KoinJavaComponent.get(SessionStore::class.java)
            } catch (_: Exception) {
                null
            }

            val redisInfo = RedisMetaInfo(
                enabled = redisConfig?.enabled ?: false,
                urlPresent = redisConfig?.url?.isNotBlank() ?: false,
                sessionTtlSeconds = redisConfig?.sessionTtlSeconds ?: 0L,
                storeType = sessionStore?.javaClass?.simpleName ?: "UNAVAILABLE",
            )

            val response = MetaResponse(
                service = "shopping-assistant-backend",
                version = "v0",
                time = Instant.now().toString(),
                redis = redisInfo,
            )

            call.respond(response)
        }

        // Модуль авторизации (/api/auth)
        authRoutes()

        // Модуль профиля (backend-часть паттерна root+manifest+state+tasks)
        profileRoutes()

        // Модуль офферов (поиск предложений)
        offerRoutes()
        offerAlertsRoutes()
        offerAnalyticsRoutes()
        offerTrackingRoutes()

        // Мои товары (пользовательские офферы)
        userOffersRoutes()
        userOffersActionsRoutes()
        userOffersPriceRoutes()

        // Модуль подписок (уведомления по алертам)
        subscriptionsRoutes()

        // Модуль треков (CRUD + events)
        tracksRoutes()

        // Модуль push (FCM tokens)
        pushRoutes()

        // Хранилище фото
        storageRoutes()

        // Vision-нормализация (камера)
        visionRoutes()

        // UGC-зеркало по ссылке
        ugcRoutes()
    }
}
