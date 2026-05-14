# TECH.AUDIO public category pack v1.0 RU

`TECH_AUDIO_public_category_pack_v1_0_RU` — базовый production-grade data pack для публичной ветки `TECH.AUDIO`.

## Назначение

Покрывает аудио-товары: наушники, TWS, гарнитуры, колонки, умные колонки, саундбары, микрофоны, аудиоинтерфейсы, DAC/AMP, винил, ресиверы/усилители, студийные мониторы, плееры, радио и аудиоаксессуары.

## Граница

Не включает пресеты, коллекции, SEO-полки и merchandising. Не создаёт микрокатегории `TECH.HEADPHONES`, `TECH.AIRPODS`, `TECH.SOUNDBARS`, `TECH.MICROPHONES`.

## Data coverage

- audio_type values: 15
- attributes: 84
- values: 284
- aliases: 106
- model head seed: 69
- model aliases: 262
- golden queries: 231
- sample offers: 118
- publish sample offers: 45

## Runtime policy

Head seed покрывает только частотные/репрезентативные модели. Long-tail модели и новые линейки должны идти через runtime candidates/live values и продвигаться после IdentityCandidateGate + AliasCollisionGate.

## Status

Static data package: PASS. Runtime validators не исполнялись в sandbox и должны быть запущены при подключении к project registry.
