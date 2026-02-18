// Last synced: 2025-12-10 15:35:15
package com.example.shoppingassistant.server.ugc

import com.example.shoppingassistant.domain.ugc.UgcMirrorResult

/**
 * Контракты backend-сервиса UGC-зеркала по ссылке.
 *
 * Задача сервиса — взять внешнюю ссылку (маркетплейс, объявление и т.п.),
 * сходить в источник, распарсить страницу и вернуть сырые нормализованные
 * данные, которые потом будут использованы для автозаполнения
 * Express-объявления / товара.
 */
interface UgcMirrorService {

    /**
     * Строит UGC-зеркало по ссылке.
     *
     * На вход — произвольный URL; на выход — нормализованные данные.
     * На уровне контракта мы всегда возвращаем [UgcMirrorResult], даже если
     * распарсить страницу не удалось (в этом случае будут заполнены только
     * базовые поля, например [UgcMirrorResult.sourceUrl]).
     */
    suspend fun mirrorByUrl(url: String): UgcMirrorResult
}
