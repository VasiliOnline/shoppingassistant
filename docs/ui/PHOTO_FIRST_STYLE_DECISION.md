# Photo-First Style Decision

Checked on 2026-04-01.

## Context

This document fixes the visual direction for Shoppingassistant before UI refactoring.
The decision is based on:

- the user reference pack collected on 2026-04-01;
- the current app UI foundation in `InputRow`, `VisualSearchCameraScreen`, `VisualSearchSheet`, `OfferCard`, and the active Compose theme;
- current patterns from official product pages and help docs for Google Lens, Pinterest Lens, Google Photos, Apple Visual Look Up, and eBay image-based shopping flows.

## Final Direction

Approve a calm `photo-first utility UI`:

- light-first;
- neutral background;
- white cards and sheets;
- one blue accent for action and focus;
- large rounded search and sheet geometry;
- image prominence over chrome;
- progressive disclosure instead of exposing every control at once.

This is the most user-friendly direction for a commerce flow started from a photo, because it reduces visual noise and keeps the object, not the interface, as the main subject.

## What To Keep From The Reference Pack

### Strong references

1. Google Translate desktop
   - Keep the clean split structure, strong input hierarchy, large working surfaces, and restrained accent use.
   - Do not copy the amount of empty space as-is into mobile screens.

2. Google Contacts / Phone / Messages empty and utility screens
   - Keep the large search pill, soft neutral background, friendly empty states, and restrained bottom navigation.
   - Keep the feeling of calm utility, not the exact density.

3. Google account and Chrome sign-in sheets
   - Keep the rounded bottom sheet pattern, grouped sections, and one obvious primary action.
   - This is a strong pattern for login, account, import, and confirm actions.

4. Google Play category chips
   - Keep chips as light refiners, not as the main visual event.
   - One visible row is enough above the fold.

### Partial references only

1. Telegram chat list
   - Good only for dense lists such as activity, chats, subscriptions, or history.
   - Not suitable as the core aesthetic for search, capture, or discovery.

2. YouTube feed and settings
   - Good only for content browsing and plain settings lists.
   - Too noisy for the main photo-search flow.

3. Large centered onboarding cards
   - Fine for first run and zero-state moments.
   - Wrong for everyday working screens because they delay the first useful action.

### Reject as core style

- launcher-like icon grids as the main composition;
- multiple equally strong actions in one bar;
- too many chip rows;
- Telegram-level density in the main discovery flow;
- heavy grey fills everywhere instead of layered white surfaces;
- decorative visuals that compete with the captured image.

## External Benchmark Signals

The following patterns were verified on 2026-04-01 from official sources. Some UI conclusions below are direct observations; some are explicit inferences from the product behavior shown there.

1. Google Lens
   - The official product positions the camera and screenshot entry as the start of search, and shopping results appear as cards layered over or after the image.
   - Inference: the photo must stay visually dominant while refinement stays secondary.

2. Pinterest Lens
   - Official help describes pointing the camera at an item, tapping a specific object in the image, and seeing visual ideas or exact products.
   - Inference: direct object targeting is more valuable than dumping the user into a generic filter wall.

3. Google Photos
   - Google Photos product messaging keeps search conversational and content-led instead of form-led.
   - Inference: natural-language hints and lightweight suggestions are preferable to dense control panels.

4. Apple Visual Look Up
   - Apple exposes recognition affordances only when the content supports them.
   - Inference: advanced photo intelligence should feel context-aware, not permanently shouting for attention.

5. eBay image-based shopping
   - Official eBay materials treat image entry as a quick shopping shortcut and separately emphasize clean, readable product photos.
   - Inference: image quality guidance and confident capture framing directly affect perceived usefulness.

## Approved Style Patterns

### 1. Search-first entry with photo as the main shortcut

- Keep one dominant search field with a camera affordance.
- Photo search should feel like the fastest path, not a secondary utility hidden in menus.
- Link and voice actions are useful, but they should not compete with photo on the default state.

### 2. Calm neutral base

- Background: soft neutral, close to `#F7F7F8`.
- Main surfaces: white.
- Surface separation: thin outlines and subtle tonal shifts, not stacked grey blocks.
- Accent: one primary blue for focus, CTA, selection, and key indicators.

### 3. Large rounded geometry

- Search field: pill or near-pill.
- Cards: medium-large rounding.
- Sheets: larger radius than cards.
- Chips: soft rounded capsules with clear selected state.

This is the most consistently pleasant pattern across the reference pack.

### 4. One obvious action per screen

- Camera screen: capture is primary.
- Review screen: continue / search is primary.
- Empty state: first useful action is primary.
- Settings sheet: save / continue is primary.

Everything else should visually step back.

### 5. Image-first composition

- The selected photo or live preview must stay large.
- Supporting guidance should sit on translucent overlays, sheets, or compact cards.
- Do not let filters, chips, and hints visually dominate the object being searched.

### 6. Progressive disclosure

- Show only the minimum needed to move forward.
- Advanced refinement appears after the first capture or behind a secondary affordance.
- Never show every intent, crop mode, category, and recovery action at once above the fold if the user has not asked for it.

### 7. Friendly utility empty states

- Empty states should be airy and human, similar to Google Messages and Contacts.
- Use illustration only when it supports orientation.
- Always pair it with one useful next action.

### 8. Dense lists only where density is the job

- Activity, messages, or tracked items can be denser.
- Main discovery and visual-search entry should stay open and calm.

## Layout Grammar

The style should be defined not only by color, but by hierarchy, order, spacing, and interaction pressure.

### Core hierarchy

Each primary screen should visually read in this order:

1. main object or main task;
2. one primary action;
3. one layer of refinement;
4. everything secondary.

If a user sees three blocks with equal visual weight in the first viewport, the screen is too noisy.

### Spacing rhythm

Use a restrained comfortable rhythm:

- screen horizontal padding: `16dp`;
- compact internal padding: `12dp`;
- major section gap: `16dp`;
- minor element gap: `8dp`;
- micro gap inside badges or helper rows: `4dp` to `6dp`.

The current project values in `LayoutDefaults` are already close to the correct baseline.

### Corner system

Use a small set of radii and repeat them consistently:

- chips / compact tags: `10-14dp`;
- cards and compact panels: `16-18dp`;
- sheets and hero helper panels: `24dp`;
- search pill and capsule controls: `28dp` or fully pill-shaped.

The interface should feel soft and technical, not playful.

### Surface layering

Recommended layering order:

- app background: neutral light grey;
- primary content surfaces: white;
- secondary support surfaces: very light grey or translucent overlays;
- only one elevated emphasis surface at a time.

Avoid stacking a tinted card inside another tinted card inside a sheet. That pattern reduces clarity fast.

## Component Decisions

### Search pill

This is the entry signature of the product and should be visually stable across screens.

Recommended pattern:

- height: `56dp`;
- full-width or near-full-width;
- one leading search icon;
- text area takes most of the width;
- camera is the strongest secondary action;
- link and voice should not all be equally visible in the default idle state unless there is a clear product reason.

Behavior:

- idle state should look calm, not tool-heavy;
- on focus, secondary utilities may expand;
- on empty state, suggestions should support the user, not visually replace the input.

### Chips

Chips are refinement tools, not primary composition.

Rules:

- show one strong row above the fold;
- keep selected chips visually clear but not loud;
- avoid more than `5-6` equally weighted options in the first viewport;
- move long-tail controls behind a “more filters” or secondary step;
- in photo review, categories should appear only if the recognition confidence is weak or the user asks to refine.

### Bottom sheets

The Google account / Chrome / Play references are strong because the sheet structure is disciplined.

Approved order:

1. title and close;
2. short orientation text if needed;
3. main content preview;
4. one small support card or message;
5. refinements;
6. primary CTA at the bottom.

Behavior:

- the first visible action should already allow progress;
- CTA should stay visually anchored near the bottom;
- do not make the user scroll through chips before they can continue.

### Cards

Product cards should feel quick to scan.

Rules:

- photo first;
- title second;
- price third;
- meta fourth;
- badges last.

Recommended card grammar:

- white surface;
- light outline or almost invisible separation;
- media ratio around `4:3` is correct for commerce;
- title max `2` lines;
- meta max `1` line;
- visible badges max `2`;
- secondary actions go into overflow, not into the primary face of the card.

### Empty states

Use the Google Messages / Contacts logic:

- generous breathing room;
- one illustration or icon cluster;
- short human headline;
- one sentence of support text;
- one CTA.

Do not add multiple competing buttons to an empty state.

## Screen Recipes

### Main entry screen

Above the fold should contain:

1. search pill;
2. maybe one row of useful refiners or suggestions;
3. either recent/helpful content or a clear empty-state block.

It should not open with:

- multiple action strips;
- several chip rows;
- a heavy onboarding card before the user can act.

### Camera / capture screen

Best pattern from the reference set plus current project direction:

- full-bleed dark live preview;
- only two top controls: dismiss and compact mode indicator;
- one compact guidance panel near the top or lower third;
- one dominant shutter control centered at the bottom;
- gallery action visually secondary;
- mode switcher close to the shutter, not dominating the viewport.

Behavior:

- camera chrome should fade into the background;
- if capture succeeds, move immediately into review instead of making the user confirm too many times;
- helper text should be short and confidence-building.

### Review / refinement screen

This is where many products become noisy. The approved pattern is:

1. large photo preview first;
2. one short system interpretation block;
3. primary CTA;
4. optional refinements under it or one step lower.

Recommended proportions:

- photo preview should occupy the strongest block in the first viewport;
- target preview height on phones: roughly `220-280dp`;
- refinement chips should not visually outweigh the preview.

Behavior:

- if confidence is high, let the user continue immediately;
- if confidence is low, show one guided refinement path instead of all refinement paths;
- manual crop, category, and intent should not all compete in the same first screen region.

### Results list

Keep the page visually quieter than the card content:

- neutral background;
- white cards;
- modest top controls;
- filters in one row or tucked into a sheet;
- cards separated by spacing, not by heavy borders.

The list should feel like a reading surface, not a dashboard.

## Typography And Copy Pressure

The current Manrope direction is correct.

Usage rules:

- large titles only for real screen anchors;
- most utility screens should rely on `bodyLarge`, `bodyMedium`, `titleSmall`, and `titleMedium`;
- helper copy should usually be one line, sometimes two, rarely more;
- avoid giant onboarding headlines on repeat-use screens.

The visual tone should be practical and calm, not promotional.

## Motion And Behavior

Motion should reinforce continuity, not advertise itself.

Approved motion rules:

- short fade / slide / scale transitions around `180-240ms`;
- camera to review should feel immediate;
- sheets should rise cleanly without bounce;
- chip selection should feel responsive but subtle;
- avoid decorative motion on every card or panel.

Behavioral principle:

- always reduce the number of decisions before the first useful result.

## Background Strategy

Background should support the object and content, not become a visual layer of its own.

Approved:

- flat neutral background for working screens;
- dark full-bleed background on camera screens;
- occasional very subtle tonal contrast between page and surface.

Not approved:

- strong gradients as the default app background;
- busy textures behind cards;
- decorative shapes on operational screens.

## Density Policy

Not every screen should have the same density.

Comfortable density:

- main search;
- camera;
- review;
- creation flow;
- empty states.

Medium density:

- result lists;
- profile summaries;
- actionable account sheets.

High density only where justified:

- tracked activity;
- chat-like logs;
- admin or diagnostic areas.

## Patterns To Avoid

- more than one row of high-priority chips above the fold in photo review;
- equal visual weight for photo, link, voice, and clear actions in the same bar;
- multiple filled cards stacked inside another filled sheet;
- generic onboarding cards inserted before the first search;
- oversized headlines on everyday utility screens;
- dark, glossy, or gradient-heavy commerce UI for this product;
- hard visual separation between capture, review, and results when a smooth flow is possible.

## Project Mapping

### `app/src/main/java/com/example/shoppingassistant/ui/theme/Theme.kt`

- Keep the current calm Material 3 foundation.
- This file is already closer to the approved direction than the alternate palette in `app/src/main/java/com/example/shoppingassistant/App.kt`.
- When refactoring, prefer neutral background plus white surface layering over stronger grey fills.

### `feature/src/main/java/com/example/shoppingassistant/feature/pages/main/ui/InputRow.kt`

- Keep the large rounded search entry.
- Make photo the most obvious secondary action.
- Reduce the feeling that photo, link, voice, and clear are all equally important.
- Preserve `56dp` field height and `28dp` pill geometry as a core pattern.
- Consider hiding lower-priority actions behind focus state or a secondary affordance.

### `feature/src/main/java/com/example/shoppingassistant/feature/pages/main/visualsearch/VisualSearchCameraScreen.kt`

- The full-bleed camera base is correct.
- Keep the dark live preview and large capture control.
- Simplify surrounding chrome and keep guidance compact.
- Keep the top bar extremely light.
- Ensure the shutter remains the single visual anchor of the bottom area.

### `feature/src/main/java/com/example/shoppingassistant/feature/pages/main/visualsearch/VisualSearchSheet.kt`

- This screen should move toward a larger photo preview, fewer first-screen chips, and a stronger primary CTA.
- Intent and focus controls should be progressive, not all fighting for the top area at once.
- Current risk: too many equal-weight refinement blocks stacked in sequence.
- Preferred order: preview, short interpretation, CTA, then refinement.

### `feature/src/main/java/com/example/shoppingassistant/feature/ui/cards/OfferCard.kt`

- Keep media-first cards and restrained metadata.
- Use badges sparingly.
- Prefer cleaner surfaces and stronger image emphasis over more decoration.
- Keep actions hidden until intent is clear.
- Preserve quick scan order: image, title, price, meta, badges.

### `feature/src/main/java/com/example/shoppingassistant/feature/pages/localoffer/LocalOfferPhotoCaptureScreen.kt`

- The direction is compatible with the approved style.
- Keep capture guidance short and confidence-building.
- Do not overload the first capture screen with too many stacked helper panels.

## Approval Summary

Approve:

- soft Material 3 utility aesthetic;
- Manrope typography;
- light-first palette;
- white cards on a neutral background;
- search pill plus camera entry;
- rounded sheets;
- sparse chip usage;
- photo-first review flow;
- friendly empty states;
- dense UI only for list-heavy secondary screens.

Do not approve:

- Telegram density as the main product style;
- YouTube-like feed noise for search flows;
- launcher-grid compositions;
- aggressive multi-accent palettes;
- grey-on-grey block stacking;
- control-heavy photo review screens.

## Recommended Implementation Order

1. Refine `InputRow` so the default entry state clearly favors search plus photo.
2. Simplify `VisualSearchCameraScreen` to one dominant action and lighter supporting chrome.
3. Rework `VisualSearchSheet` into a stronger photo preview plus progressive refinement flow.
4. Tighten `OfferCard` and shared tokens so results feel cleaner and more premium.
5. After that, align empty states and account sheets with the same visual language.

## Sources

- Google Lens: https://lens.google/
- Pinterest Lens help: https://help.pinterest.com/en/article/pinterest-lens
- Google Photos: https://www.google.com/photos/about
- Apple Visual Look Up support: https://support.apple.com/en-ng/guide/iphone/iph37cfc27f4/ios
- eBay Buy Browse API `searchByImage`: https://developer.ebay.com/api-docs/buy/browse/resources/item_summary/methods/searchByImage
- eBay image guidelines for listings: https://www.ebay.com/sellercenter/resources/seller-updates/2022-winter/photos
