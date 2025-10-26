package com.example.shoppingassistant.core.di

import com.example.shoppingassistant.core.data.ProductRepository
import com.example.shoppingassistant.core.data.ProductRepositoryImpl
import org.koin.dsl.module

val coreModule = module {
    single<ProductRepository> { ProductRepositoryImpl() }
}
