package com.example.shoppingassistant.feature.chat.di

import org.koin.dsl.module
import org.koin.core.module.dsl.viewModelOf   // ← новый импорт

import com.example.shoppingassistant.feature.chat.ChatViewModel


val chatModule = module {
    // Конструкторный биндинг: Koin сам подставит зависимости из конструктора
    viewModelOf(::ChatViewModel)
}
