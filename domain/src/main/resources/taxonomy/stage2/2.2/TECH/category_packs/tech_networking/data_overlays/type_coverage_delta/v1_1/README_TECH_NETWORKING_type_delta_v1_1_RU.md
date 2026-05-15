# TECH.NETWORKING type coverage delta v1.1 RU

Patch-пакет закрывает P1 data gaps после проверки `TECH_NETWORKING_public_category_pack_v1_0_RU`.

## Scope
- Patch type: `data_overlay_patch`
- Target: `TECH.NETWORKING`
- No schema changes
- No UI surface changes
- No presets/collections
- No new categoryCode

## Patched areas
- ANTENNA: outdoor/LTE/MIMO/directional antenna fixtures and aliases
- NETWORK_ACCESSORY: RJ45, patch cord, patch panel, keystone, network tester/tool fixtures
- MODEM: GPON/ONT/ONU and XGS-PON aliases/fixtures
- ROUTER: GL.iNet/Cudy/Ruijie/Reyee/DrayTek head values
- Route boundary: Ethernet/RJ45 network accessories stay in TECH.NETWORKING; USB/HDMI/power cables and Russian "сетевой фильтр" stay in TECH.POWER_CHARGING_CABLES

## Why
Base package was data-covered, but low-frequency networking types had weak model seed/publish coverage. This patch closes the gap without adding microcategories or merchandising layers.
