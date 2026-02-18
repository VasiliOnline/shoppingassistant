# MainPage task map (skeleton, mirrored names)
- `ui/*` mirrors `context/*` file names for easy discovery.
- Context = pure functions, UI = thin wrappers with sensible defaults.
# MainPage — mirrored skeleton (ready to wire)
- UI ↔ Context files share names (InputSection, FiltersStrip, Suggestions, OffersPlaceholder, TopOffers).
- ViewModel uses ProductRepository + RankService (from your core modules).
- Top-3 UI reuses your existing `feature.chat.ui.Top3Carousel` for now.
- FilterSheetUI temporarily reused from `feature.chat.ui.context`.
