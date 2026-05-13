# FASH.SHOES production pack v1.0 RU

**categoryCode:** `FASH.SHOES`  
**pack_type:** `category_schema_pack`  
**version:** `1.0.0`  
**locale:** `ru-RU`  
**status:** production candidate  
**generated_at:** `2026-05-13T00:00:00+02:00`

## Назначение

Пакет фиксирует самостоятельную ветку обуви в FASH-каноне. Он не создаёт отдельных веток `FASH.MEN_SHOES`, `FASH.WOMEN_SHOES`, `FASH.KIDS_SHOES`: пол и возраст держатся атрибутами `target_gender` и `age_group`.

Режим зрелости выбран как **brand-aware + optional `model_name_text`**. Это даёт поддержку Nike / Adidas / Ecco / Reima / Geox / Zara и популярных текстовых моделей вроде Air Force 1, Samba, Ultraboost, но не требует curated model graph в P0.

## Ключевая схема

Минимум для публикации:

- `shoe_type`
- `condition`
- `pair_completeness`
- `size_eu` **или** `raw_size_text`

Рекомендуемое ядро пользовательского качества:

- `target_gender`
- `age_group`
- `brand`
- `color_primary`
- `season`
- `material_upper`

## Типы обуви

Пакет покрывает P0/P1-типы: кроссовки, беговую и спортивную обувь, бутсы, ботинки, сапоги, зимнюю обувь, треккинговую обувь, классические туфли, лоферы, мокасины, оксфорды, дерби, сандалии, шлёпанцы, домашние тапочки, балетки, каблуки, лодочки, танкетки, резиновые сапоги, детскую обувь, пинетки, сабо/кроксы, эспадрильи и fallback `OTHER_SHOES`.

## Route conflict layer

В пакет добавлен общий файл `route_conflict_layer.fash.v1_0.yaml`. Он задаёт порядок разрешения конфликтов между:

- `FASH.SHOES`
- `FASH.APPAREL_COMMON` / `FASH.MEN` / `FASH.WOMEN` / `FASH.KIDS`
- `FASH.BAGS`
- `FASH.ACCESSORIES`
- `TECH.WEARABLES`

Примеры ожидаемой маршрутизации:

| Запрос | Route |
|---|---|
| мужские кроссовки | `FASH.SHOES` |
| женские сапоги | `FASH.SHOES` |
| детские ботинки | `FASH.SHOES` |
| мужская футболка | `FASH.APPAREL_COMMON` |
| женское платье | `FASH.APPAREL_COMMON` |
| детская куртка | `FASH.APPAREL_COMMON` |
| рюкзак nike | `FASH.BAGS` |
| ремень кожаный | `FASH.ACCESSORIES` |
| apple watch / смарт-часы | `TECH.WEARABLES` |

## Размерная политика

`size_eu` — канонический размер. `raw_size_text` сохраняет исходную размерную метку продавца. Конвертация US/UK/CM/RU в EU не форсируется без явной системы размеров или будущей брендовой таблицы.

Для детской обуви `foot_length_cm` и `insole_length_cm` полезны как дополнительные измерения, но не подменяют `size_eu` автоматически.

## Vision rules

Из изображения можно поддерживать извлечение `shoe_type`, `color_primary`, `closure_type`, `heel_type`, `shaft_height`, `condition` и `pair_completeness` с confidence-ограничениями. Нельзя выводить размер без читаемой бирки/текста, пол по цвету, бренд без читаемого логотипа/текста или оригинальность товара.

## Состав пакета

```text
schema_pack.fash_shoes.v1_0.json
attributes.fash_shoes.v1_0.tsv
values.fash_shoes.v1_0.tsv
aliases.fash_shoes.ru.v1_0.tsv
conditional_rules.fash_shoes.v1_0.yaml
size_policy.fash_shoes.v1_0.yaml
route_conflict_layer.fash.v1_0.yaml
routing_guardrails.fash_shoes.v1_0.yaml
vision_rules.fash_shoes.v1_0.yaml
user_surface.fash_shoes.v1_0.yaml
facet_schema.fash_shoes.v1_0.yaml
golden_queries.fash_shoes.v1_0.tsv
sample_offers.fash_shoes.v1_0.jsonl
integration_mapping.fash_shoes.v1_0.yaml
quality_gates.fash_shoes.v1_0.yaml
validation_report.fash_shoes.v1_0.json
checksums.sha256.json
README_FASH_SHOES_PRODUCTION_v1_0_RU.md
```

## Нецели v1.0

- полноценный sneaker model graph;
- брендовые размерные таблицы;
- автоматическое подтверждение оригинальности;
- автоматическая конвертация длины стопы в EU-размер.

## Рекомендуемый следующий шаг

После стабилизации `FASH.SHOES` логично выпускать `FASH.BAGS`, затем `FASH.ACCESSORIES`, чтобы accessories остались управляемой residual-веткой, а не ловушкой для сумок, обуви и wearable-tech.
