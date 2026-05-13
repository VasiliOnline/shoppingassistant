# LocalOffer Scope Freeze And Gap Analysis

Date: 2026-03-26

## Scope Freeze

Current canonical scope for `localoffer` is only the seller create flow:

- `session -> geo snapshot -> draft create/update -> preview -> publish preflight -> publish`
- canonical client entrypoint is `LocalOfferSheet`
- canonical server API is `/api/localoffer/*`
- legacy `shortlisting` UI is no longer the canonical client path

Explicitly out of current scope:

- editing an already published `offerId`
- `offerId -> reopen draft` semantics
- in-place edit of a live offer
- moderation rules for post-publish edits

This means the current contract may be fixed as the canonical create-flow contract, but not yet as the full seller-offer product contract.

## Realized Coverage

Approximate implementation coverage:

- contract/runtime coverage: `90-93%`
- product-complete coverage for the frozen create-flow scope: `84-88%`

## Status Update

Closed on 2026-03-26:

- `localoffer` now owns native domain types instead of re-exporting `shortlisting` type aliases
- `LocalOfferBackendServiceImpl` now depends on `LocalOfferDraftRuntime`, not directly on `ShortListingBackendService`
- public draft namespace is normalized to canonical `lodraft-*`
- state machine was pruned to reachable states
- client now has a dedicated `BLOCKED_STATE`
- published-offer edit is formally treated as out of current product contract and routed into `BLOCKED_STATE`
- contract tests now cover canonical draft namespace, blocked routing, geo freshness, revision conflict, publish idempotency and client flow-step restore

Still open after this update:

- published-offer edit remains intentionally out of scope for this contract
- the runtime engine still uses a compatibility adapter internally, but that adapter is now accepted as the production runtime for the frozen create-flow scope

What is already implemented:

- dedicated domain contract and repository surface in `domain/localoffer` and `core/data/localoffer`
- dedicated authenticated API under `/api/localoffer/*`
- native `localoffer` domain models with no typealias coupling to `shortlisting`
- mandatory geo gate before draft creation
- `preview-before-publish` and separate `publish preflight`
- request-level `revision`, `effectiveSpecVersion`, `geoSnapshotId`, `publishCommandId`
- server-side publish command persistence and conflict checks
- separation between `PENDING_REVIEW` and buyer-visible `LIVE`
- canonical public draft namespace normalized to `lodraft-*`
- dedicated client `BLOCKED_STATE`
- canonical client routing moved from legacy `shortlisting` UI to `localoffer`
- server and feature tests covering namespace, blocked routing, geo freshness, revision conflicts, publish idempotency and persisted flow-step restore

## What Is Strong

The following contract laws are implemented well enough to treat `localoffer` as the canonical create-flow path:

1. Geo gate is enforced before draft creation.
   Evidence: `LocalOfferSheet` does not create a draft before geo confirmation, and `LocalOfferBackendServiceImpl.createDraft` requires `geoSnapshotId`.

2. Publish is not available directly from review.
   Evidence: the client opens `preview`, then `preflight`, and only `preflight` owns the publish action.

3. Runtime-envelope fields needed for stale-client protection exist on the localoffer boundary.
   Evidence: `revision`, `effectiveSpecVersion`, `geoSnapshotId`, `publishCommandId` are carried through preflight and publish.

4. Moderation visibility is separated from buyer-facing live visibility.
   Evidence: `publication_state` was introduced so `PENDING_REVIEW` no longer behaves as `LIVE`.

## Remaining Gaps

These are the remaining reasons the contract should not be described as the full seller-offer product contract beyond the frozen create-flow scope.

### 1. Compatibility runtime is still legacy-backed under the hood

`LocalOfferBackendServiceImpl` no longer depends on `ShortListingBackendService` directly, but the owned `LocalOfferDraftRuntime` adapter still delegates to it under the hood.

Evidence:

- `server/src/main/kotlin/com/example/shoppingassistant/server/localoffer/LocalOfferService.kt`
- `server/src/main/kotlin/com/example/shoppingassistant/server/localoffer/LocalOfferDraftRuntime.kt`
- `server/src/main/kotlin/com/example/shoppingassistant/server/shortlisting/ShortListingService.kt:112`

Why this matters:

- `localoffer` now owns the runtime boundary and public contract
- legacy analysis and publish behavior still power the compatibility adapter internally
- this is accepted for the frozen create-flow product state, but it remains an internal implementation debt rather than a contract gap

### 2. UI ownership is still composed inside one sheet shell

The current client path is canonical, but it is not yet a strong implementation of the contract's child-node ownership model.

Evidence:

- one `ModalBottomSheet` hosts geo, review, preview, preflight and result in `feature/src/main/java/com/example/shoppingassistant/feature/pages/localoffer/LocalOfferSheet.kt:70`
- `PreviewCard` and `PreflightCard` are inline sections of the same sheet
- issue routing to `BLOCKED_STATE` exists on the server side, but the client has no dedicated blocked-state owner route

Why this matters:

- node ownership is enforced in state and actions, not by separate navigation stacks
- `BLOCKED_STATE` is now a dedicated owner-state, but not a separate route graph
- this is acceptable for the frozen create-flow contract, but still a UX architecture simplification

### 3. Published-offer edit is intentionally unsupported

Opening a non-draft `offerId` for edit routes into the new flow, but the flow explicitly rejects it as an unsupported legacy target.

Evidence:

- `app/src/main/java/com/example/shoppingassistant/AppRoot.kt:127`
- `feature/src/main/java/com/example/shoppingassistant/feature/pages/localoffer/LocalOfferViewModel.kt:467`
- `feature/src/main/java/com/example/shoppingassistant/feature/pages/localoffer/LocalOfferSheet.kt:262`

Why this matters:

- if the product definition includes seller editing after publish, the contract is not complete
- this is only acceptable if post-publish edit remains explicitly out of scope

## Decision

Current recommendation:

- fix `localoffer` now as the product-frozen canonical create-flow contract
- do not describe it as the finished seller-offer product contract beyond create-flow
- keep published-offer edit explicitly out of scope for this phase

## Exit Criteria To Call It Product-Complete

Do not call the contract fully finished until all items below are closed:

1. Replace the `LocalOfferDraftRuntime` compatibility adapter with a fully owned engine if the team wants to retire the last legacy implementation debt.
2. Split `BLOCKED_STATE` and other child nodes into separate route owners if the team wants structural UX parity with the original child-pattern package.
3. Decide product policy for published-offer edit:
   either a separate contract/package, or explicit non-support in product scope.
4. Extend tests further if needed:
   moderation visibility, resume after full app/process restart with persisted storage, and broader end-to-end UI coverage.

## Verification Notes

Known verification status at the time of this freeze:

- localoffer route, service-boundary, server-contract and feature flow-step coverage exist
- `:domain:compileKotlin :core:compileDebugKotlin :server:compileKotlin :feature:compileDebugKotlin :app:compileDebugKotlin` passed after the update
- `:feature:testDebugUnitTest --tests "com.example.shoppingassistant.feature.pages.localoffer.LocalOfferViewModelTest"` passed
- `:server:test --tests "com.example.shoppingassistant.server.localoffer.LocalOfferServiceBoundaryTest" --tests "com.example.shoppingassistant.server.localoffer.LocalOfferContractIntegrationTest" --tests "com.example.shoppingassistant.server.localoffer.LocalOfferRoutesTest"` passed
