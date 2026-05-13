# FASH.BAGS production pack v1.0 RU

**Pack ID:** `FASH.BAGS@1.0.0-ru`  
**categoryCode:** `FASH.BAGS`  
**Тип пакета:** `category_schema_pack`  
**Статус:** production-ready baseline  
**Дата сборки:** 2026-05-13

## Что покрывает пакет

Пакет фиксирует самостоятельную ветку **FASH.BAGS**: сумки, рюкзаки, чемоданы, кошельки, кардхолдеры, косметички, деловые и дорожные форматы.

Главное решение по зрелости: **brand-aware + optional `model_name_text`**, без обязательного curated brand/family/model graph. Это позволяет нормально обрабатывать Nike, Adidas, Michael Kors, Longchamp, Fjallraven, Samsonite и luxury-бренды, но не тянуть P0-пакет в бесконечный model long-tail.

## Типы верхнего уровня

В `bag_type` включены:

- `BACKPACK`, `SCHOOL_BAG`, `DRAWSTRING_BAG`
- `HANDBAG`, `SHOULDER_BAG`, `CROSSBODY_BAG`, `TOTE`, `SHOPPER_BAG`, `CLUTCH`, `WAIST_BAG`
- `LAPTOP_BAG`, `LAPTOP_SLEEVE`, `BRIEFCASE`, `DOCUMENT_BAG`
- `TRAVEL_BAG`, `DUFFEL_BAG`, `SUITCASE`
- `WALLET`, `CARDHOLDER`, `COSMETIC_BAG`, `DIAPER_BAG`, `BEACH_BAG`

## Ключевое routing-решение

`WALLET` и `CARDHOLDER` намеренно закреплены за **FASH.BAGS**, а не за **FASH.ACCESSORIES**. Практически пользователь ищет кошельки рядом с сумками, портмоне и small leather goods.

## Guards

Пакет содержит жёсткие отсечения:

- кроссовки / сапоги / ботинки → `FASH.SHOES`
- футболки / платья / куртки → `FASH.MEN`, `FASH.WOMEN`, `FASH.KIDS`
- ремни / шарфы / шапки / очки / украшения → `FASH.ACCESSORIES`
- Apple Watch / smartwatch / фитнес-браслет / phone case → `TECH`
- переноски для животных → `PET.ACCESSORIES`
- tool/cooler/medical/camera bags → соответствующие non-fashion ветки или review
- копия / реплика luxury bag → `POLICY_REVIEW`

Приоритет: **head noun важнее бренда**. Например, `рюкзак Nike` остаётся `FASH.BAGS`, а `кроссовки Nike` уходит в `FASH.SHOES`.

## Файлы

- `schema_pack.fash_bags.v1_0.json` — основной schema pack
- `attributes.fash_bags.v1_0.tsv` — атрибуты и применимость
- `values.fash_bags.v1_0.tsv` — enum-значения и aliases значений
- `aliases.fash_bags.ru.v1_0.tsv` — RU aliases, positive/negative routing signals
- `conditional_rules.fash_bags.v1_0.yaml` — условные правила по типам
- `size_policy.fash_bags.v1_0.yaml` — размеры, объём, laptop fit, cabin logic
- `routing_guardrails.fash_bags.v1_0.yaml` — route guards
- `route_conflict_layer.fash_bags.v1_0.yaml` — локальный FASH conflict layer для сумок
- `vision_rules.fash_bags.v1_0.yaml` — vision/multimodal extraction rules
- `user_surface.fash_bags.v1_0.yaml` — UI surface, группы и фильтры
- `facet_schema.fash_bags.v1_0.yaml` — facet schema
- `golden_queries.fash_bags.v1_0.tsv` — golden routing/extraction queries
- `sample_offers.fash_bags.v1_0.jsonl` — sample offers
- `integration_mapping.fash_bags.v1_0.yaml` — mapping для ingest/search/facet pipeline
- `quality_gates.fash_bags.v1_0.yaml` — quality gates
- `validation_report.fash_bags.v1_0.json` — отчёт валидации
- `checksums.sha256.json` — SHA-256 checksums

## Минимальное ядро атрибутов

Для большинства листингов достаточно:

`bag_type`, `brand`, `condition`, `color_primary`, `material_outer`, `size_class`, `target_gender`, `age_group`.

Дальше включаются type-specific поля:

- `SUITCASE`: `wheels_count`, `wheel_type`, `lock_type`, `cabin_size_status`, `volume_liter`, `luggage_shell_type`
- `LAPTOP_BAG` / `LAPTOP_SLEEVE`: `fits_laptop_inch`, `laptop_protection_level`, `document_size`
- `SCHOOL_BAG`: `school_grade_group`, `orthopedic_back`, `reflective_elements`
- `WALLET` / `CARDHOLDER`: `card_slots_count`, `coin_pocket`, `document_size`
- `CROSSBODY_BAG` / `WAIST_BAG`: `strap_type`, `strap_length_cm`, `is_adjustable_strap`

## Интеграционная позиция

Этот пакет можно подключать после `FASH.APPAREL_COMMON` hardening и до `FASH.ACCESSORIES`. Он уже содержит локальный conflict layer, поэтому не должен конфликтовать с будущими `FASH.SHOES` и `FASH.ACCESSORIES` при условии, что глобальный router уважает `head noun > brand token` и negative guards.
