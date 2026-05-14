# TECH_AUDIO_brand_head_delta_pack_v1_1_RU

Минимальный patch/data-overlay для `TECH.AUDIO`.

## Назначение

Закрывает P1-дыру базового пакета `TECH_AUDIO_public_category_pack_v1_0_RU`: недостаточный head-слой audio-first брендов для брендового фасета и поиска.

## Граница

Это НЕ новая категория и НЕ расширение дерева.

- `public_category: false`
- `accepts_offers_directly: false`
- новых `categoryCode`: 0
- пресеты/коллекции/витринные полки: нет
- SEO landing pages: нет
- полный model registry: нет

## Что меняет

Добавляет category-scoped head brand values для `TECH.AUDIO#brand` и alias delta для RU/Latin spelling.

## Файлы

- `audio_brand_head_delta.tech_audio.v1_1.tsv`
- `audio_brand_aliases_delta.ru.tech_audio.v1_1.tsv`
- `brand_facet_smoke_queries.tech_audio.v1_1.tsv`
- `sample_offers.audio_brand_delta.v1_1.jsonl`
- `integration_mapping.tech_audio_brand_delta.v1_1.yaml`
- `quality_gates.tech_audio_brand_delta.v1_1.yaml`
- `effective_spec_snapshot.expected.tech_audio_brand_delta.v1_1.json`
- `validation_report.tech_audio_brand_delta.v1_1.json`

## Runtime-правило

Brand values не являются закрытым enum. Этот delta добавляет head-values для качества фасета и поиска, а long-tail бренды продолжают идти через runtime candidates/live values.
