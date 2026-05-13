**Listing Vision Eval**

`localoffer` AI now has an offline eval runner for real photo-sets:

`./gradlew.bat :server:runListingVisionEval -PevalManifest=C:\\path\\to\\listing_vision_eval_manifest.json -PevalOutput=C:\\path\\to\\listing_vision_eval_report.json`

Manifest format:
- `manifestName`: optional report label
- `locale`: default locale for all samples
- `samples[]`: labeled photo-sets

Each sample supports:
- `sampleId`
- `categoryHint`
- `expectedCategoryCode`
- `expectedFieldCodes`
- `expectedFields`
- `hints`
- `photos[]` with `path` and `role`

Reported summary:
- `latencyP50Ms`
- `latencyP95Ms`
- `averageFillRate`
- `categoryHitRate`

`fillRate` is computed as:
- predicted expected-field count / labeled expected-field count

Use real seller photo-sets and keep `expectedFieldCodes` limited to fields that should be recoverable from photos or tech labels.
