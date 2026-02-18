# Stage 2.1 L0 — SPORT (Спорт и активный отдых): пакет наполнения (product)

Версия: v1.0  
Дата: 2026-02-13  

## Состав пакета
- `GG_Taxonomy_Stage_2_1_L0_SPORT_Packages_v1_0_RU.docx` — master ТЗ для агента
- `browse_nodes.sport.tsv` — BrowseNode дерево для UI/роутинга
- `aliases.sport.tsv` — алиасы/синонимы + blocked noise
- `routing_rules.sport.yaml` — нормализация, дизамбигуация, scoring, fallback
- `queries_golden.sport.tsv` — golden-набор тестовых запросов (≥200)
- `coverage.sport.json` — конфиг coverage-гейта для CI/валидатора

## Ключевые дизамбигуации (важное)
- витамины/омега/БАД/коллаген → `BEAUTY.HEALTH`
- инструмент/ремкомплект/набор инструментов → `HOME.REPAIR_TOOLS`
- “как/упражнения/программа тренировок/скачать” → BLOCKED (контент, не оффер)
- одежды/обуви “спорт” отдельного листа в текущей канонике нет: спорт‑одежду не маршрутизируем в FASH автоматически (оставляем в SPORT или BLOCKED по правилу).
