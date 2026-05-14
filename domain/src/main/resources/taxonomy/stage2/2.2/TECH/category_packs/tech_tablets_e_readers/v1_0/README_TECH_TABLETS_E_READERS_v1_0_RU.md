# TECH_TABLETS_E_READERS_public_category_pack_v1_0_RU

Production-grade public category pack для `TECH.TABLETS_E_READERS`.

## Scope

Покрывает:
- TABLET — обычные планшеты
- KIDS_TABLET — реальные детские планшеты, не игрушки
- E_READER — электронные книги / e-readers
- GRAPHICS_TABLET — графические планшеты и pen displays
- TABLET_DOCK — tablet-specific docks
- TABLET_STAND — tablet stands / holders

## Boundary

Пакет не создаёт микрокатегории `TECH.IPAD`, `TECH.KINDLE`, `TECH.WACOM`, `TECH.E_READER`.
Бренды/модели живут в identity/spec слоях и head seed, а не в categoryCode.

## Excluded

- presets / collections / shelves / SEO landing pages
- TECH.PHONES
- TECH.COMPUTERS
- TECH.MONITORS_DISPLAYS
- TECH.COMPUTER_ACCESSORIES
- full global model registry

## Runtime status

Static package: PASS. Реальные project validators должны быть прогнаны после mount в registry.
