# TECH.PHONES public category pack v1.0 RU

**Пакет:** `TECH_PHONES_public_category_pack_v1_0_RU.zip`  
**categoryCode:** `TECH.PHONES`  
**pack_type:** `public_category_pack`  
**Статус:** production-ready static candidate, готов к runtime mount после foundation.

## Назначение

Пакет покрывает публичную категорию **TECH.PHONES**: смартфоны, кнопочные телефоны, складные телефоны, защищённые телефоны, ретро/коллекционные и спутниковые телефоны.

Пакет наследует:

- `TECH.ELECTRONICS_COMMON`
- `TECH.DEVICE_IDENTITY_COMMON`
- `TECH.SPECS_COMMON`

`TECH.COMPATIBILITY_COMMON` не наследуется как основной слой телефона: телефоны являются target devices для аксессуаров, а не аксессуарами.

## Что НЕ входит

- `TECH.PHONE_ACCESSORIES` implementation
- `TECH.IPHONE`, `TECH.SAMSUNG_PHONES`, `TECH.CASES`, `TECH.CHARGERS`, `TECH.CABLES`
- raw IMEI storage
- полный глобальный registry моделей

## Runtime принцип

`categoryCode` остаётся `TECH.PHONES`; бренды/модели/линейки — это identity/facets/shelves, а не новые категории.

## Ключевые файлы

- `schema_pack.tech_phones.v1_0.json`
- `attributes.tech_phones.v1_0.tsv`
- `values.tech_phones.v1_0.tsv`
- `model_head_seed.tech_phones.v1_0.tsv`
- `phone_model_aliases.tech_phones.ru.v1_0.tsv`
- `route_guardrails.tech_phones.v1_0.yaml`
- `vision_rules.tech_phones.v1_0.yaml`
- `golden_queries.tech_phones.v1_0.tsv`
- `sample_offers.tech_phones.v1_0.jsonl`
- `publish_sample_offers.tech_phones.v1_0.jsonl`
- `effective_spec_snapshot.expected.tech_phones.v1_0.json`
- `quality_gates.tech_phones.v1_0.yaml`
- `validation_report.tech_phones.v1_0.json`

## Важные gates

- `IdentityCandidateGate`
- `PhoneModelSeedGate`
- `NoModelGuessGate`
- `PhoneResaleSafetyGate`
- `IMEIPrivacyGate`
- `RouteConflictGate`
- `SampleOffersSmokeGate`

## Следующий пакет

`TECH_PHONE_ACCESSORIES_public_category_pack_v1_0_RU.zip`
