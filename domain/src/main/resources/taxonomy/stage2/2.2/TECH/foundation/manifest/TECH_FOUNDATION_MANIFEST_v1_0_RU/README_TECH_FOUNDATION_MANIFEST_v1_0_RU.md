# TECH_FOUNDATION_MANIFEST v1.0 RU

**Назначение:** маленький manifest/index для TECH foundation, а не большой bundle с данными.

Пакет связывает четыре уже созданных shared-standard пакета:

1. `TECH.ELECTRONICS_COMMON`
2. `TECH.DEVICE_IDENTITY_COMMON`
3. `TECH.SPECS_COMMON`
4. `TECH.COMPATIBILITY_COMMON`

## Граница пакета

```text
pack_type: foundation_manifest_pack
public_category: false
accepts_offers_directly: false
runtime_mount: catalog_foundation_manifest_registry
```

Manifest **не содержит** attributes/values/aliases payload из shared-пакетов. Он хранит только индексы, SHA-256, порядок подключения, dependency graph, gates и expected foundation snapshot.

## Почему так

Правильная схема TECH foundation — не “засунуть четыре жирных shared-пакета в один архив”, а подключить их по одному и затем закрепить foundation manifest. Это сохраняет полноту данных в каждом shared-пакете и не заставляет генератор/агента урезать values, aliases, golden queries и fixtures.

## Порядок mount

```text
0. TECH_CATEGORY_TREE_CONTRACT_v1_0
1. TECH.ELECTRONICS_COMMON
2. TECH.DEVICE_IDENTITY_COMMON
3. TECH.SPECS_COMMON
4. TECH.COMPATIBILITY_COMMON
5. TECH_FOUNDATION_MANIFEST_v1_0_RU
```

## Запрещено внутри manifest

```text
TECH.PHONES public category pack
TECH.PHONE_ACCESSORIES public category pack
TECH.IPHONE
TECH.CASES
TECH.CHARGERS
TECH.CABLES
model head seed
attributes/values/aliases copied from shared packs
```

## Runtime gates

Static self-check в этом пакете — `PASS`. После импорта в проект нужно прогнать project runtime validators:

```text
Stage22SeedValidator
FacetSchemaValidator
EffectiveSpecSnapshotGate
RouteConflictGate
AliasCollisionGate
VisionRulesGate
UserSurfaceGate
T3HiddenLeakGate
```

## Следующий пакет

После зелёного foundation mount следующий пакет:

```text
TECH_PHONES_public_category_pack_v1_0_RU
```

Это именно public category pack `TECH.PHONES`, не отдельная категория `TECH_PHONES_IDENTITY_PACK`.
