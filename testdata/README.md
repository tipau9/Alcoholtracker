# testdata

Shared fixtures that more than one implementation reads. Nothing here is
consumed by the shipping iOS app at runtime.

## bac_vectors.json

Golden vectors for the BAC engine: 18 cases, each pinning what
`BACCalculator` produces for a given profile, drink sequence, and set of
session events. This is the contract the planned Android/Kotlin port is
tested against, so an independent Widmark implementation cannot silently
disagree about a permille number.

Shape:

- `constants` -- the physical constants and clamps the Swift engine uses.
  A port that hardcodes different ones is already wrong.
- `tolerance` -- 1e-6. Every double is rounded to 9 places, so a
  regenerated file is byte identical unless the engine actually changed.
- `originEpochSeconds` -- the fixed epoch all `offsetMinutes` are relative
  to. No vector depends on the wall clock.
- `vectors[].input` -- profile, stomach status, conservative flag, drinks
  (with the `rawContribution` and `absorptionWindowMinutes` the engine
  derived), vomit offsets, meal events.
- `vectors[].derived` -- the intermediate chain: validated body data, total
  body water, distribution factor, effective elimination rate, gastric
  minutes, peak factor. When a port disagrees on the final number, this is
  where you find out which step diverged.
- `vectors[].expected` -- projected peak for the first drink, session peak,
  hours until sober, hours until the driving limit, and a BAC sample every
  15 minutes.

### Regenerating

Requires a macOS runner; the generator is Swift.

```
gh workflow run build-ipa.yml --ref <branch>
gh run download <run-id> -n bac-vectors
```

The `bac-vectors` job launches the app in a simulator with
`-emitBACVectors`, which makes
[BACVectorEmitter](../Alcoholtracker/BACVectorEmitter.swift) print one JSON
line per vector. The job reassembles them, asserts the age clamp resolves
from the pinned integer rather than the clock, and uploads the result.

A diff in the regenerated file means the BAC engine changed. Read the diff
before committing it.
