# Stage 2.2 SoT

Rules:
- Stage 2.2 source of truth lives only in `resources`.
- Stage 2.2 source of truth must not be moved to Kotlin code or local developer-only files.
- CLOSED enum-like values must be stored only as `valueCode`.
- Arbitrary free-form strings must not be used instead of `valueCode` for CLOSED sets.
- `profile` and `constraint` entries must not reference missing `attributeCode`.
- `profile` and `constraint` entries must not reference missing `valueCode`.

Structure contract:
- `_registry/` for registry-level files.
- `_global/` for global Stage 2.2 files.
- `<L0>/` directories mirror Stage 2.1 L0 directories.
- `.keep` files preserve empty skeleton folders.

Registry files:
- `_registry/attributes.json` — canonical attribute registry (`attributeCode`, value type/set type, flags, labels, normalization).
- `_registry/value_dictionaries.json` — canonical dictionaries for `CLOSED`/`SEMI_CLOSED` attributes (`valueCode`, labels, aliases).
- `_registry/registry_meta.json` — schema/data versions and generation timestamp.
- `_registry/package_descriptors.json` — deterministic list of Stage 2.2 L0 package descriptors.
- `_registry/leaf_profile_empty_allowlist.json` — explicit allowlist of leaf categories that may keep empty attribute profile.
