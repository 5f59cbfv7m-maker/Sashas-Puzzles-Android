# Working on this project

Android port of the iOS app in `../JigsawPuzzle` (Sasha's Puzzles). Kotlin, Jetpack Compose, no network.
The iOS project is the reference for behaviour, wording and design; read its `CLAUDE.md` for the reasoning
behind the engine. This file records what is specific to Android.

## Build and test

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:testDebugUnitTest :app:lintDebug   # must pass before a change is done
./gradlew :app:installDebug                        # onto the running emulator or a USB phone
```

There is no system JDK on this Mac; Android Studio's bundled JBR is the one to use. The SDK is in
`~/Library/Android/sdk` (`local.properties`, git-ignored). The foldable emulator is the AVD `Fold76`
("7.6in Foldable", Android 16): `~/Library/Android/sdk/emulator/emulator -avd Fold76`.

## Where things live

| Path | Role |
|---|---|
| `engine/` | `PuzzleGeometry`, `EdgeProfile`, `PuzzleState`, `Viewport`, `SplitMix64` — pure Kotlin, unit-tested, a line-by-line port of iOS `Sources/Engine` |
| `render/PieceTextures.kt` | One bitmap per piece, bevel included, cut in parallel |
| `game/GameSession.kt` | The live game: drag, snap, undo, clock, autosave, viewport policy |
| `game/BoardView.kt`, `TrayView.kt`, `GameScreen.kt`, `Overlays.kt` | The playing screen |
| `app/AppModel.kt` | Navigation, saves, stats; lives in `PuzzleApp`, not the activity |
| `persistence/PlayerStats.kt` | The solved-games history and everything derived from it, including `Achievement` |
| `library/ProfileScreen.kt`, `game/Achievements.kt` | The profile, the medals and their reveal on the completion card |
| `ui/Theme.kt` | The iOS design tokens, fonts and shared controls |
| `Scripts/import-ios-strings.py` | Regenerates every `strings.xml` from the iOS `Localizable.xcstrings` |

## Invariants carried over from iOS

- Neighbouring pieces share one identical curve; geometry regenerates from `(seed, columns, rows)`.
  `SplitMix64` and `mixSeed` match the Swift ones bit for bit (`EngineTest.splitMixMatchesReference`),
  and the daily picture uses Foundation's day-of-era count, so both apps pick the same one.
- Two pieces are joined iff their groups share a translation; a group at `.zero` is locked.
- Board units are resolution independent. Folding, rotating or split screen only change `Viewport`.

## Android-specific decisions

**Folding never recreates the activity.** The manifest handles `screenSize|smallestScreenSize|screenLayout|…`
itself, and the game lives in `PuzzleApp.model`, so a fold is a Compose re-layout of the same session.
`GameSession.boardLaidOut` clamps on every size and re-fits once the shape flips or the width changes by
more than 30 % (fold ↔ unfold). The emulator's `adb emu fold` does not resize the display for apps on this
image; test a fold with `adb shell wm size 904x2316` and `adb shell wm size reset` mid-game.

**Compact means narrower than 600dp** (`LocalCompact`): the cover screen and ordinary phones. The unfolded
Fold (~670dp) is tablet layout, but narrower than an iPad, so the game header folds its secondary actions
into the menu below 780dp and drops the progress bar below 900dp; the title goes below 400dp.

**Piece bitmaps are painted through a `BitmapShader`, not a clip.** Software `clipPath` is aliased; an
anti-aliased fill is not. The bevel strokes use `SRC_ATOP` so they stay inside the piece. The texture budget
is 32 M pixels (iOS: 90 M) — Android kills a game that eats memory sooner.

**Drawing uses the native canvas with float rects.** Compose's `drawImage` takes `IntOffset`/`IntSize`;
rounding each piece to whole pixels opens hairline gaps between joined pieces.

**A composable that reads a bitmap must read `textures.revision` itself.** Bitmaps land in place; a tray
cell that only received `session` as a parameter was skipped and kept its placeholder forever.

**Tray drags decide in the touch slop.** The cell's pointer handler runs before the lazy grid's scroll
(child first in the main pass): across the scroll axis, or after a 250 ms hold, it consumes and lifts the
piece; along the axis it lets go and the grid scrolls.

**Fonts.** Caprasimo and Figtree have no Cyrillic or CJK. Android draws missing glyphs with the regular
system face whatever weight was asked, so `Theme.install` builds the display typeface with
`Typeface.CustomFallbackBuilder` (system sans at weight 900) on Android 10+.

**Strings are generated.** Never edit `res/values*/strings.xml`; change the iOS catalog and rerun the
script. iOS plural substitutions inside one string become `<plurals name="…_argN">` plus a string argument
(`ui/Text.kt` `substituted`). Names that are Java keywords get `_label` (`R.string.continue_label`).

**Achievements are derived, never stored.** `PlayerStats.isUnlocked` reads the history; only the keys
the player has looked at are saved (`seenAchievements`, `null` until the first launch that knows them, so
an update does not flag old medals as new). Keys are the iOS raw values. "A week in a row" uses the longest
streak ever, so the medal is not taken back when a streak breaks. The chime is `res/raw/achievement.wav`,
written by `Scripts/make-achievement-tone.py` from the partials the iOS app synthesizes.

**Own photos are copies.** `Library.importPhoto` decodes the picked image upright (ImageDecoder on
Android 9+, which also reads HEIC; EXIF rotation by hand before that), caps it at 4096 px and writes a JPEG to
`files/Photos/`, listed in `files/library.json`. The photo picker needs no storage permission. Deleting a photo
also deletes its games in progress and its cached copies.

**Signing.** The upload key and `keystore.properties` never enter the repository (git-ignored). Debug
builds install on a phone over USB without any certificate and do not expire.

## Checking screens

Debug builds accept a stage: `adb shell am start -n com.kirillrychkov.sashaspuzzles/.MainActivity --es stage completed`
(`board`, `scattered`, `completed`, `profile`); add `--es achievements sprinter,nightmare` to make the
completion card reveal those medals, and `--es item <id>` to play a given picture (an own photo's id is in
`files/library.json`). Screenshots: `adb exec-out screencap -p > shot.png`. Language:
`adb shell cmd locale set-app-locales com.kirillrychkov.sashaspuzzles --locales ru-RU`; dark:
`adb shell cmd uimode night yes`.
