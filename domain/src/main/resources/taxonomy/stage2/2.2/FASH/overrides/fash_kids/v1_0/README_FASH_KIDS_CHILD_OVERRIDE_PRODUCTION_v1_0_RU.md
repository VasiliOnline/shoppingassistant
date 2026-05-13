# FASH.KIDS v1.0 — Child Override Production Pack

`FASH.KIDS` — публичная дочерняя ветка детской одежды, наследующая общий стандарт `FASH.APPAREL_COMMON`.

Пакет не дублирует common-атрибуты. Он добавляет только детские overrides:
- defaults и allowed values для `target_gender` / `age_group`
- приоритеты типов одежды
- детские размерные системы: рост, возрастные labels, RU kids
- алиасы и route guardrails
- vision rules
- user surface и facet overrides
- fixtures, golden queries, expected effective spec snapshot

## Runtime-модель

```text
FASH.KIDS = FASH.APPAREL_COMMON + child overrides
```

Главное отличие от `FASH.MEN` / `FASH.WOMEN`:
- ключевыми становятся `age_group`, `height_cm`, `age_label`
- нельзя угадывать пол/возраст ребёнка по цвету, принту или стилю
- baby gear / toys / stroller / car seats должны маршрутизироваться в `KIDS.*`, не в `FASH.KIDS`

## Перед merge

Обязательно прогнать:
```text
Stage22SeedValidator
FacetSchemaValidator
FashKidsChildOverrideGate
FashRouteConflictGate
EffectiveSpecSnapshotGate
```
