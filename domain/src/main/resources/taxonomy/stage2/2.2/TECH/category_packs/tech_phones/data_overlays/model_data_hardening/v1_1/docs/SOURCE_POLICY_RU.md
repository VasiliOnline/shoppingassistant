# Source policy

Пакет использует head seed подход. Это не полный мировой реестр телефонов.

Уровни source_policy:

- official_apple_compare_2026_05 — модели из официальной страницы сравнения Apple.
- official_samsung_or_market_head_seed — официальные/рыночные head-модели Samsung, мягкий seed.
- official_google_store_or_market_head_seed — официальные/рыночные head-модели Pixel, мягкий seed.
- official_xiaomi_or_market_head_seed — официальные/рыночные head-модели Xiaomi/Redmi/POCO, мягкий seed.
- market_head_seed_soft — популярные модели для поиска; требуют периодического обновления через runtime candidates/live values.

Ни один source_policy не превращает seed в hard validation registry.
