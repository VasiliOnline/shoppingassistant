# TECH_ELECTRONICS_COMMON shared standard pack v1.0 RU

**Pack ID:** `TECH_ELECTRONICS_COMMON_shared_standard_pack_v1_0_RU`  
**Category code:** `TECH.ELECTRONICS_COMMON`  
**Type:** `shared_standard_pack`  
**Public category:** `false`  
**Accepts offers directly:** `false`

## Назначение

Пакет задаёт общий стандарт электроники для всех public-веток `TECH`: бренд как общий признак, производитель, состояние, цвет, год выпуска, гарантия, регион/версия страны, комплектация, seller bundle, оригинальная коробка, refurbished/repair-история и confidence полноты объявления.

Это **не** public category и не финальная точка маршрутизации. Пакет должен монтироваться в `shared_standard_registry` и наследоваться будущими public packs.

## Что входит

- `schema_pack.tech_electronics_common.v1_0.json`
- `attributes.tech_electronics_common.v1_0.tsv`
- `values.tech_electronics_common.v1_0.tsv`
- `aliases.tech_electronics_common.ru.v1_0.tsv`
- `user_surface.tech_electronics_common.v1_0.yaml`
- `facet_templates.tech_electronics_common.v1_0.yaml`
- `generated_alias_rules.tech_electronics_common.v1_0.yaml`
- `route_guardrails.tech_electronics_common.v1_0.yaml`
- `vision_rules.tech_electronics_common.v1_0.yaml`
- `golden_queries.tech_electronics_common.v1_0.tsv`
- `sample_offers.tech_electronics_common.v1_0.jsonl`
- `integration_mapping.tech_electronics_common.v1_0.yaml`
- `quality_gates.tech_electronics_common.v1_0.yaml`
- `effective_spec_snapshot.expected.tech_electronics_common.v1_0.json`
- `validation_report.tech_electronics_common.v1_0.json`
- `checksums.sha256.json`

## Жёсткие границы

Не добавлено внутрь:

- `TECH.DEVICE_IDENTITY_COMMON`
- `TECH.SPECS_COMMON`
- `TECH.COMPATIBILITY_COMMON`
- `TECH.PHONES`
- `TECH.PHONE_ACCESSORIES`
- brand/family/model registry
- detailed specs registry
- compatibility refs
- public microcategories: `TECH.IPHONE`, `TECH.CASES`, `TECH.CHARGERS`, `TECH.CABLES`

## Роли атрибутов

- `T0_CORE`: brand, manufacturer, condition, color_family, release_year, warranty_status, region, country_version
- `T1_TYPE_RELEVANT`: included_accessories, seller_bundle, original_box, refurbished_status, repair_history, cosmetic_condition_grade, functional_condition
- `T2_OPTIONAL_ENRICHMENT`: warranty/detail/completeness/refurbishment notes
- `T3_SYSTEM_HIDDEN`: model raw text, confidence/evidence/source/trace fields

## Runtime принцип

`TECH.ELECTRONICS_COMMON` не должен принимать офферы напрямую. Если router дал этот shared standard как final route — это ошибка `SharedStandardNoFinalRouteGate`.

## Следующий пакет

Следующим должен идти:

`TECH_DEVICE_IDENTITY_COMMON_shared_standard_pack_v1_0_RU`

А уже позже:

- `TECH_SPECS_COMMON_shared_standard_pack_v1_0_RU`
- `TECH_COMPATIBILITY_COMMON_shared_standard_pack_v1_0_RU`
- `TECH_FOUNDATION_MANIFEST_v1_0_RU`
- `TECH_PHONES_IDENTITY_PACK_v1_0_RU`
