# TECH.COMPUTER_ACCESSORIES public category pack v1.0 RU

Пакет: `TECH_COMPUTER_ACCESSORIES_public_category_pack_v1_0_RU`  
Категория: `TECH.COMPUTER_ACCESSORIES`  
Тип: `public_category_pack`  
Статус: baseline complete / static production candidate.

## Назначение

Покрывает базовую public-ветку компьютерных аксессуаров:

- клавиатуры
- мыши
- комплекты клавиатура + мышь
- веб-камеры
- док-станции
- USB-хабы
- подставки для ноутбуков
- кронштейны/руки для мониторов
- коврики для мыши
- стилусы
- адаптеры/донглы
- охлаждающие подставки
- computer covers / hard shell / keyboard covers
- screen/privacy filters
- cleaning kits

## Граница

Не включает:
- пресеты, коллекции, shelves, SEO landing pages
- `TECH.COMPUTERS`
- `TECH.MONITORS_DISPLAYS`
- `TECH.PC_COMPONENTS`
- `TECH.STORAGE_MEMORY`
- `TECH.POWER_CHARGING_CABLES`
- микрокатегории вроде `TECH.KEYBOARDS`, `TECH.MICE`, `TECH.DOCKS`

## Main type attribute

`computer_accessory_type`

## Runtime-принцип

Детализация живёт в:
- `computer_accessory_type`
- facets
- compatibility fields
- specs fields
- aliases
- route guards

А не в новых categoryCode.

## Static self-check

- attributes: 74
- values: 140
- aliases: 184
- model_head_seed: 61
- model_aliases: 160
- golden_queries: 120
- sample_offers: 122
- publish_sample_offers: 45
- initial_fields: 8 / 8
- primary_facets: 7 / 7

Реальные project validators нужно запускать после mount в registry.
