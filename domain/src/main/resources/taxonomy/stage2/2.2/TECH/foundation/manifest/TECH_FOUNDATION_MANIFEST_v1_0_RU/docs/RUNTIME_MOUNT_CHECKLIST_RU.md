# RUNTIME MOUNT CHECKLIST RU

## До mount

- Проверить SHA-256 всех четырёх referenced ZIP.
- Проверить zip integrity.
- Проверить, что все referenced packs имеют `public_category=false` и `accepts_offers_directly=false`.
- Проверить, что нет `TECH.PHONES`, `TECH.PHONE_ACCESSORIES` и микрокатегорий.

## Mount order

1. `TECH.ELECTRONICS_COMMON`
2. `TECH.DEVICE_IDENTITY_COMMON`
3. `TECH.SPECS_COMMON`
4. `TECH.COMPATIBILITY_COMMON`

## После mount

- Пересчитать actual effective foundation snapshot.
- Сравнить с `effective_spec_snapshot.expected.tech_foundation.v1_0.json`.
- Прогнать validators из `quality_gates.tech_foundation.v1_0.yaml`.
- Сформировать integration report.

## Release condition

Foundation можно считать подключённым только когда все P0 gates green.
