# TECH_COMPATIBILITY_COMMON shared_standard pack v1.0 RU

**Pack:** `TECH_COMPATIBILITY_COMMON_shared_standard_pack_v1_0_RU`  
**CategoryCode:** `TECH.COMPATIBILITY_COMMON`  
**Type:** `shared_standard_pack`  
**Public category:** `false`  
**Accepts offers directly:** `false`

## Назначение

Пакет задаёт общий standard совместимости для TECH: аксессуар, компонент, расходник, ПО или сервис связывается с target-устройством, семейством, платформой, разъёмом, протоколом, размером, mount или power profile.

Это не public category и не пакет `TECH.PHONE_ACCESSORIES`. Он не создаёт `TECH.IPHONE`, `TECH.CASES`, `TECH.CHARGERS`, `TECH.CABLES`, `TECH.MACBOOK`, `TECH.AIRPODS`.

## Зависимости

Монтировать после:

1. `TECH.ELECTRONICS_COMMON`
2. `TECH.DEVICE_IDENTITY_COMMON`
3. `TECH.SPECS_COMMON`

## Static self-check

- attributes: 63
- values: 331
- aliases: 628
- golden_queries: 129
- sample_offers: 136
- compatibility surface rows: 18
- initial fields: 8 / 8
- primary facets: 7 / 7
- T3 hidden leaks: 0

## Runtime validators

В sandbox не запускались. После подключения прогнать gates из `quality_gates.tech_compatibility_common.v1_0.yaml`.
