# TECH_PHONES_model_data_hardening_pack_v1_1_RU

Компактный production-oriented hardening-пакет для `TECH.PHONES`.

## Цель

Усилить runtime-поиск и нормализацию популярных телефонов по оси:

```text
brand → manufacturer_brand → family → line → model → storage/ram hints
```

Пакет **не** является новой категорией и **не** принимает офферы напрямую.
Он накладывается поверх `TECH_PHONES_public_category_pack_v1_0_RU` как `data_overlay_only`.

## Почему это отдельный, но один пакет

Здесь собраны только данные, без которых runtime будет слабым на популярных запросах:

1. brand/family policy для head-семейств;
2. model head seed delta;
3. русские/живые aliases моделей;
4. минимальная variant matrix для parsing/dedup hints;
5. search priority seed для подсказок;
6. минимальные smoke fixtures/gates.

## Что принципиально не входит

```text
presets
collections
landing_pages
merchandising shelves
SEO-посадочные
compatibility refs
TECH.PHONE_ACCESSORIES
полный мировой model registry
```

## Файлы

```text
manifest.json
brand_family_head_seed.tech_phones.v1_1.tsv
model_head_seed_delta.tech_phones.v1_1.tsv
model_aliases_delta.ru.tech_phones.v1_1.tsv
model_variant_minimal_matrix.tech_phones.v1_1.tsv
model_search_priority_seed.tech_phones.v1_1.tsv
identity_resolution_min_policy.tech_phones.v1_1.yaml
golden_queries.tech_phones_model_data.v1_1.tsv
sample_offers.tech_phones_model_data.v1_1.jsonl
integration_mapping.tech_phones_model_data.v1_1.yaml
quality_gates.tech_phones_model_data.v1_1.yaml
effective_spec_snapshot.expected.tech_phones_model_data.v1_1.json
validation_report.tech_phones_model_data.v1_1.json
checksums.sha256.json
checksums.sha256.txt
```

## Static summary

```text
brand families: 46
model seed rows: 121
model aliases: 1128
variant hint rows: 121
golden queries: 313
sample offers: 126
```

## Runtime stance

`model_variant_minimal_matrix` — это `soft_suggest_only`: она помогает парсить `iphone 13 128` или `s24 ultra 512`, но не должна сама отклонять оффер.

`model_search_priority_seed` — это вес для identity suggestions, а не пресет/коллекция.

## Next

После этого не надо ещё ходить по кругу вокруг `TECH.PHONES`; можно переходить к следующей базовой public-ветке `TECH.PHONE_ACCESSORIES`.
