# TECH_POWER_CHARGING_CABLES_route_publish_delta_pack_v1_1_RU

Patch-пакет поверх `TECH_POWER_CHARGING_CABLES_public_category_pack_v1_0_RU`.

## Назначение
Закрыть P1 data-gap текущей ветки перед переходом дальше:

1. Добавить publish fixtures для `SURGE_PROTECTOR`.
2. Добавить publish fixtures для `CABLE_MANAGEMENT`.
3. Усилить route-guard для неоднозначного запроса `сетевой адаптер`: power adapter остается в `TECH.POWER_CHARGING_CABLES`, Wi-Fi/Ethernet/RJ45 adapter уходит в `TECH.NETWORKING`.

## Границы

- Не меняет schema.
- Не меняет UI surface.
- Не добавляет presets / collections / SEO shelves.
- Не создает новые categoryCode или микрокатегории.
- Не переносит USB hubs/docks/network cables в power branch.

## Итог

`TECH.POWER_CHARGING_CABLES` после patch: data-covered YES, patch needed NO, move next YES.
