# TECH_PRINTERS_SCANNERS_consumables_compatibility_delta_pack_v1_1_RU

Лёгкий patch для `TECH.PRINTERS_SCANNERS` после проверки базового пакета v1.0.

## Цель
Закрыть P1-дыру по consumables: популярные коды картриджей/тонеров/чернил, aliases, smoke queries и publish fixtures.

## Не входит
- новые categoryCode;
- пресеты/коллекции;
- полный мировой registry картриджей;
- жёсткая валидация совместимости.

## Политика
`compatible_cartridge_code` и `compatible_printer_model_text` используются как soft search/dedup hints. Они не блокируют публикацию в v1.1.

## Вердикт
После применения patch ветка `TECH.PRINTERS_SCANNERS` считается data-covered на baseline production level.
