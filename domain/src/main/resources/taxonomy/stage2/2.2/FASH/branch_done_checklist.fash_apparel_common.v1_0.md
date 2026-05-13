# FASH.APPAREL_COMMON Shared Standard Checklist

- [x] Common attributes stored once in shared_profiles.fash.json and inherited by FASH.MEN, FASH.WOMEN, FASH.KIDS.
- [x] FASH.APPAREL_COMMON is not created as a public categoryCode.
- [x] `measurements` declared as a UI group, not an attribute.
- [x] `dictionary_value` alias target format documented.
- [x] Attribute dictionaries are global registry dictionaries reused by inherited runtime profiles.
- [x] Stage 3 facet definitions added for apparel runtime facets.
- [x] Stage 4 facet presentation profile hides system fields.
- [x] Route guard golden queries cover shoes, bags, and accessories conflicts.
- [x] FASH.MEN child override pack can still add male-specific priorities and aliases.
- [x] FASH.WOMEN child override pack tunes defaults after MEN validation.
- [x] FASH.KIDS child override pack tunes child sizing, gender/age defaults, and negative route guards.
