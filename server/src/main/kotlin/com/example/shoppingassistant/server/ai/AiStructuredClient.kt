package com.example.shoppingassistant.server.ai

import com.example.shoppingassistant.server.ai.yandex.YandexAiExecutionTarget
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioClient
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioContentPart
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioFailureCode
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioFocusRegion
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioInvocationMode
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioStructuredRequest
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioStructuredResponse
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioTransportConfig

// This file is the adapter seam for future AI providers.
// The runtime uses neutral names from server.ai, while Yandex remains the active backend today.
typealias AiTransportConfig = YandexAiStudioTransportConfig
typealias AiContentPart = YandexAiStudioContentPart
typealias AiFocusRegion = YandexAiStudioFocusRegion
typealias AiExecutionTarget = YandexAiExecutionTarget
typealias AiStructuredRequest = YandexAiStudioStructuredRequest
typealias AiInvocationMode = YandexAiStudioInvocationMode
typealias AiFailureCode = YandexAiStudioFailureCode
typealias AiStructuredResponse = YandexAiStudioStructuredResponse
typealias AiStructuredClient = YandexAiStudioClient
