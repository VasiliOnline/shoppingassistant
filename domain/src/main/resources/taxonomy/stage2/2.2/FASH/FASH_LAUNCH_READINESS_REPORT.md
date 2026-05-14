# FASH Launch Readiness Report

## Category Tree

Production tree is fixed by `fash_category_tree_contract.v1_0.json`.

```text
FASH
├─ FASH.APPAREL_COMMON        shared
├─ FASH.MEN                   public
├─ FASH.WOMEN                 public
├─ FASH.KIDS                  public
├─ FASH.SHOES                 public
├─ FASH.BAGS                  public
└─ FASH.ACCESSORIES           public
```

New public FASH branches require review. Examples that must stay in type/facets unless reviewed: `FASH.SNEAKERS`, `FASH.JEWELRY`, `FASH.WATCHES`, `FASH.MEN_SHOES`, `FASH.WOMEN_BAGS`.

## Connected Packs

- `FASH_APPAREL_COMMON_SHARED_STANDARD_v1_0`
- `FASH_MEN_CHILD_OVERRIDE_v1_0`
- `FASH_WOMEN_CHILD_OVERRIDE_v1_0`
- `FASH_KIDS_CHILD_OVERRIDE_v1_0`
- `FASH_SHOES_PRODUCTION_v1_0`
- `FASH_BAGS_PRODUCTION_v1_0`
- `FASH_ACCESSORIES_PRODUCTION_v1_0`
- `FASH_TYPED_ATTRIBUTE_COVERAGE_MATRIX_v1_0`

## Typed Attribute Coverage

`fash_typed_attribute_coverage_matrix.v1_0.json` is the runtime contract for type-level FASH coverage. It covers every allowed type value from the effective constraints for all six public branches:

- `FASH.MEN`: `apparel_type`, 36 values
- `FASH.WOMEN`: `apparel_type`, 46 values
- `FASH.KIDS`: `apparel_type`, 37 values
- `FASH.SHOES`: `shoe_type`, 26 values
- `FASH.BAGS`: `bag_type`, 22 values
- `FASH.ACCESSORIES`: `accessory_type`, 45 values

Total covered type values: `212`.

The matrix defines category-level and type-group-level runtime surfaces: initial fields, primary/secondary facets, required fields, hidden/system fields, validator coverage, non-facet explanations and AI/vision no-guess attributes.

Runtime resolution is implemented by `FashTypeSurfaceResolver`, which merges category defaults with the matching type group and returns the effective type surface used by gates and UI/runtime integration.

## Validators

- `catalog_pack_v2` contract, archetype assignment and manifest coverage gates
- fixed FASH tree contract gate
- cross-branch `fash_route_conflict_golden.tsv`
- effective spec snapshot gates for all six public branches
- UI surface smoke gates: initial fields <= 8, primary facets <= 7, no hidden-field leaks
- visual/search no-guess golden cases
- type surface golden gates for `JEANS`, `DRESS`, `SCHOOL_UNIFORM`, `SNEAKERS`, `BACKPACK`, `BELT` and `WATCHES_NON_SMART`
- typed attribute coverage gates for all T0/T1/T3 FASH fields
- Stage 2.2 seed validator and route conflict gates

## Route Conflict Summary

Golden cases cover apparel, shoes, bags, accessories and TECH route-out:

- `мужская футболка` -> `FASH.MEN`
- `женское платье` -> `FASH.WOMEN`
- `детская куртка` -> `FASH.KIDS`
- `мужские кроссовки` -> `FASH.SHOES`
- `женская сумка` -> `FASH.BAGS`
- `детский рюкзак` -> `FASH.BAGS`, not `FASH.KIDS`
- `ремень кожаный` -> `FASH.ACCESSORIES`
- `очки солнцезащитные` -> `FASH.ACCESSORIES`
- `apple watch` -> `TECH.WEARABLES`, not `FASH.ACCESSORIES`

Expected route conflict failures: `0`.

## UI Surface Summary

All public FASH branches have launch snapshots with:

- initial fields <= 8
- primary typed facets <= 7
- hidden/system fields excluded from user-visible primary UI
- every referenced typed field present in the effective spec or explicitly treated as a UI grouping field
- every public T0/T1 field covered by a runtime surface, validator and facet template or explicit non-facet explanation
- type-specific checks for `JEANS`, `DRESS`, `SCHOOL_UNIFORM`, `SNEAKERS`, `BACKPACK`, `BELT` and `WATCHES_NON_SMART`

## Vision/Search Summary

Visual golden cases enforce:

- do not infer `MEN`/`WOMEN` from a plain apparel photo without text/context
- shoes can route to `FASH.SHOES`, but size/brand/model need text or visible label/logo evidence
- bags can route to `FASH.BAGS`, but brand/authenticity/laptop fit cannot be guessed from image alone
- smartwatch evidence routes to TECH, not fashion non-smart watches

## Known Limitations

- Live operational sample counts are not embedded in seed files; seed readiness uses current default operational metrics.
- `TECH.WEARABLES` is now the current catalog target for smart watches and fitness bands.
- New public FASH categoryCode additions must go through review and update the tree contract plus route conflict golden file.

## Verdict

`GO` for FASH as a unified production contour with type-aware runtime coverage, subject to keeping the contract/golden gates green.
