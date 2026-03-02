# Stage 4.0 SoT

Stage 4 materializes immutable attribute contract on top of current Stage 2.2/3.0 data:

- `immutable_attribute_schema.json`:
  - immutable snapshot of attribute schema (`attributeCode`, type/set, flags, normalization),
  - deterministic `immutableFingerprint` per attribute.
- `normalization_contract.json`:
  - normalization policy per attribute,
  - open vs dictionary-backed behavior and dedup token mode.
- `dedup_keys.json`:
  - canonical key templates for schema/dictionary/profile/facet entities and per-category identity signatures.
- `typed_constraints.json`:
  - typed runtime constraints for ingest/backfill/search contracts,
  - includes `enumOnly`, `unit`, `regex`, `min/max`, and category-aware `requiredIf`.

Rules:

- Stage 4 files must stay synchronized with:
  - `taxonomy/stage2/2.2/_registry/attributes.json`
  - `taxonomy/stage2/2.2/_registry/value_dictionaries.json`
  - `taxonomy/stage2/2.2/*/profiles.*.json` (for `requiredIf`)
  - `taxonomy/stage3/3.0/facet_*.json`
- Domain quality gate fails on any drift between Stage 4 and current model.
