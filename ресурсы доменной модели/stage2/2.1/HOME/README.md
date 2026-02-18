# Stage 2.1 L0 — HOME (Дом и ремонт): пакет наполнения (product)

Версия: v1.0  
Дата: 2026-02-13  

## Состав пакета
- `GG_Taxonomy_Stage_2_1_L0_HOME_Packages_v1_0_RU.docx` — master ТЗ для агента (структура, правила, DoD, таблицы)
- `browse_nodes.home.tsv` — BrowseNode дерево для UI/роутинга
- `aliases.home.tsv` — алиасы/синонимы + blocked noise
- `routing_rules.home.yaml` — нормализация, дизамбигуация, scoring, fallback
- `queries_golden.home.tsv` — golden-набор тестовых запросов (≥200)
- `coverage.home.json` — конфиг coverage-гейта для CI/валидатора

## Быстрый старт агенту
1) Импортируй `browse_nodes.home.tsv` и `aliases.home.tsv` в хранилище пакетов.
2) Подключи `routing_rules.home.yaml` в QueryRouter.
3) Прогони coverage-гейт из `coverage.home.json` и тест `queries_golden.home.tsv`.

## Нота по дизамбигуации (важное)
- “умная лампочка/выключатель/датчик” → TECH.SMART_HOME  
- “пылесос/робот-пылесос” → APPL.SMALL  
- “электрический чайник/блендер/кофемашина” → APPL.SMALL  
- “чайник заварочный” → HOME.KITCHEN_DINING
