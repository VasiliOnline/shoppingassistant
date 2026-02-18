# Stage 2.1 L0 — BEAUTY (Красота и здоровье): пакет наполнения (product)

Версия: v1.0  
Дата: 2026-02-13  

## Состав пакета
- `GG_Taxonomy_Stage_2_1_L0_BEAUTY_Packages_v1_0_RU.docx` — master ТЗ для агента
- `browse_nodes.beauty.tsv` — BrowseNode дерево для UI/роутинга
- `aliases.beauty.tsv` — алиасы/синонимы + blocked noise
- `routing_rules.beauty.yaml` — нормализация, дизамбигуация, scoring, fallback
- `queries_golden.beauty.tsv` — golden-набор тестовых запросов (≥200)
- `coverage.beauty.json` — конфиг coverage-гейта для CI/валидатора

## Ключевые дизамбигуации (важное)
- iphone/samsung и т.п. → `TECH.PHONES`
- пылесос/робот‑пылесос → `APPL.SMALL`
- кроссовки/обувь → `FASH.SHOES` (кроме “крем/щётка/спрей для обуви”)
- крем/щётка/спрей/дезодорант для обуви → `HOME.CLEANING`
- фен/стайлер/эпилятор/ирригатор → `BEAUTY.DEVICES`
- тонометры/линзы/ортопедия → `BEAUTY.HEALTH`
