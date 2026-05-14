# TECH.STORAGE_MEMORY public category pack v1.0 RU

Пакет: `TECH_STORAGE_MEMORY_public_category_pack_v1_0_RU`

Назначение: базовое production-grade покрытие публичной ветки `TECH.STORAGE_MEMORY`.

Ветка покрывает:
- INTERNAL_SSD
- INTERNAL_HDD
- EXTERNAL_SSD
- EXTERNAL_HDD
- USB_FLASH_DRIVE
- MEMORY_CARD
- CARD_READER
- NAS
- STORAGE_ACCESSORY

Граница:
- не создаёт `TECH.SSD`, `TECH.HDD`, `TECH.NAS`, `TECH.MEMORY_CARDS`;
- не включает presets/collections/SEO shelves;
- не заменяет `TECH.PC_COMPONENTS`: RAM modules остаются там;
- не заменяет `TECH.POWER_CHARGING_CABLES`: power banks и generic cables остаются там;
- не заменяет `TECH.COMPUTER_ACCESSORIES`: USB hubs остаются там.

Статус:
- static production candidate: PASS
- data coverage: BASELINE_PRODUCTION_DATA_COVERED
- runtime validators: не запускались в sandbox
- prod release: после mount + project validators

Файлы:
- schema_pack
- attributes
- values
- aliases
- generated_alias_rules
- user_surface
- facet_templates
- route_guardrails
- vision_rules
- golden_queries
- sample_offers
- publish_sample_offers
- model_head_seed
- model_aliases
- facet_coverage_matrix
- dedup_policy
- resale_condition_policy
- integration_mapping
- quality_gates
- effective_spec_snapshot.expected
- validation_report
- checksums
