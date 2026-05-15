# TECH.WEARABLES type coverage delta v1.1

Лёгкий patch/data-overlay для `TECH.WEARABLES`.

## Зачем
Базовый пакет `TECH_WEARABLES_public_category_pack_v1_0_RU` был data-covered по core smartwatch/fitness tracker/smart ring, но имел P1-gap по `WATCH_BAND`, `WEARABLE_ACCESSORY` и `AR_GLASSES`: мало aliases и нет publish fixtures для части типов.

## Что меняет
- Добавляет aliases и smoke queries для ремешков, wearable accessories и AR glasses.
- Добавляет publish fixtures для `WATCH_BAND`, `WEARABLE_ACCESSORY`, `AR_GLASSES`.
- Не меняет schema, UI surface, primary facets и categoryCode.

## Что не входит
Presets, collections, SEO-landing pages, merchandising shelves, new microcategory codes.

Created: 2026-05-14T16:57:17.222569+00:00
