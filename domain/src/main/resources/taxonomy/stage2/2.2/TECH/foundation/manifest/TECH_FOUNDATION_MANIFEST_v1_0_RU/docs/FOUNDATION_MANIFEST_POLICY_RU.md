# FOUNDATION MANIFEST POLICY RU

`TECH_FOUNDATION_MANIFEST_v1_0_RU` — это manifest/index, а не data pack.

## Основной принцип

Shared-пакеты должны оставаться отдельными, полноразмерными и содержательными. Manifest только фиксирует:

- какие shared-пакеты входят в foundation;
- в каком порядке они подключаются;
- какие SHA-256 должны совпасть;
- какие validators являются блокирующими;
- какой expected foundation snapshot должен получиться после mount.

## Что нельзя делать

Нельзя копировать в manifest:

- attributes из Electronics/Identity/Specs/Compatibility;
- values;
- aliases;
- golden queries;
- sample offers;
- model head seed;
- public category data.

Причина: копирование создаёт риск расхождения источников истины и урезания данных.
