# TECH.PRINTERS_SCANNERS public category pack v1.0 RU

Пакет покрывает публичную TECH-ветку `TECH.PRINTERS_SCANNERS`.

## Scope

Внутри ветки:

- PRINTER
- MULTIFUNCTION_PRINTER
- SCANNER
- LABEL_PRINTER
- INK_CARTRIDGE
- TONER_CARTRIDGE
- PRINTER_ACCESSORY

Это base category data pack: без пресетов, коллекций, SEO-полок и микрокатегорий.

## Boundary

Не создаём `TECH.PRINTERS`, `TECH.SCANNERS`, `TECH.INK`, `TECH.TONER`, `TECH.HP_PRINTERS`.

Детализация живёт в `printer_scanner_type`, фасетах, alias rules, compatibility/evidence fields и route guards.

## Runtime policy

- Cartridge/toner compatibility is evidence-first.
- Do not infer exact printer/scanner model from generic photo alone.
- Device and consumable dedup keys are separate.
- 3D printers and POS cash-register systems are outside this branch in v1.

## Status

Static production candidate. Runtime validators must be executed after registry mount.
