# TECH.SPECS_COMMON shared standard pack v1.0 RU

Пакет: `TECH_SPECS_COMMON_shared_standard_pack_v1_0_RU`

Назначение: общий shared-standard технических характеристик для всей категории TECH.

Это **не public category** и он **не принимает офферы напрямую**. Public TECH packs наследуют этот слой и через coverage matrix повышают нужные specs до required/facet fields.

## Включено

- common spec attributes: storage, RAM, screen, battery, power, connectors, wireless, OS, dimensions, weight, protection, ports, charging, network, display/audio/camera/networking primitives
- common head values и unit policies
- aliases / generated alias rules
- route guardrails для защиты от ложного TECH routing по unit/spec tokens
- vision/no-guess rules
- golden queries и sample offers
- spec coverage matrix по public TECH веткам
- unit normalization rules
- spec confidence/evidence policy
- integration mapping
- quality gates
- expected effective spec snapshot
- validation report
- checksums

## Не включено

- brand/family/model registry — это `TECH.DEVICE_IDENTITY_COMMON` и downstream identity packs
- compatibility refs — это `TECH.COMPATIBILITY_COMMON`
- `TECH.PHONES`, `TECH.PHONE_ACCESSORIES` и другие public category packs
- микрокатегории вроде `TECH.IPHONE`, `TECH.CABLES`, `TECH.CHARGERS`

## Runtime policy

Exact specs нельзя угадывать по фото без evidence. Фото может дать candidate для connector/form_factor/port presence, но не exact storage/RAM/battery/chipset/refresh rate.

## Следующий пакет

После этого пакета логично делать `TECH_COMPATIBILITY_COMMON_shared_standard_pack_v1_0_RU.zip`.
