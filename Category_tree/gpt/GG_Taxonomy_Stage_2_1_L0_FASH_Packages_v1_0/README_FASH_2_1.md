# Stage 2.1 L0 — FASH (Одежда и обувь): пакет наполнения (product)

Версия: v1.0  
Дата: 2026-02-13  

## Состав пакета
- `GG_Taxonomy_Stage_2_1_L0_FASH_Packages_v1_0_RU.docx` — master ТЗ для агента
- `browse_nodes.fash.tsv` — BrowseNode дерево для UI/роутинга
- `aliases.fash.tsv` — алиасы/синонимы + blocked noise
- `routing_rules.fash.yaml` — нормализация, дизамбигуация, scoring, fallback
- `queries_golden.fash.tsv` — golden-набор тестовых запросов (≥200)
- `coverage.fash.json` — конфиг coverage-гейта для CI/валидатора

## Ключевые дизамбигуации (важное)
- коляска/автокресло/подгузники → `KIDS.STROLLERS_CARSEATS` / `KIDS.BABY_GEAR`
- игрушки/LEGO → `KIDS.TOYS_GAMES`
- духи/парфюм → `BEAUTY.FRAGRANCE`
- смарт‑часы/фитнес‑браслет → `TECH.WEARABLES`
- перчатки хозяйственные (для уборки) → `HOME.CLEANING`
- ноутбук без bag‑токенов (рюкзак/сумка) → `TECH.COMPUTERS`
