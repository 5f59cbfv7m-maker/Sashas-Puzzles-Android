# Sasha's Puzzles for Android

The Android version of [Sasha's Puzzles](https://github.com/5f59cbfv7m-maker/Sasha-s-Puzzles), a jigsaw puzzle
with real interlocking pieces, a warm table and 120 built-in photographs. Kotlin and Jetpack Compose, fully offline.

It is made for foldables as much as for phones: on the Galaxy Z Fold's cover screen it lays out like a phone,
unfolded it lays out like a tablet, and folding or unfolding in the middle of a game keeps every piece where it was.

## What is in 1.0

- The library of 120 photographs, the daily puzzle and its streak
- Framing (original, 1:1, 3:2, 4:3, 16:9, 2:3) and 12 to 1000 pieces
- The board: drag pieces out of the tray, snapping and merging, pinch to zoom, two fingers to pan,
  hint, undo and redo, scatter and gather, the picture guide under the board
- Saved games and personal best times
- Sounds, the library and board music, haptics
- Light and dark themes, ten languages (the same translations as the iOS app)

Planned next: profile and achievements, Google Play Games leaderboards, puzzles from your own photos.

## Building

You need a Mac, Windows or Linux computer with [Android Studio](https://developer.android.com/studio).

1. Open this folder in Android Studio and let it sync.
2. To play on your phone: turn on **Developer options** (Settings → About phone → Software information → tap
   *Build number* seven times), then **USB debugging** in Developer options, plug the phone in and press **Run**.
   A build installed this way does not expire.
3. To update without the cable: turn on **Wireless debugging** in Developer options, tap *Pair device with
   pairing code* and run `adb pair <IP:port shown>` with the code. From then on `Scripts/install-on-phone.sh`
   finds the phone over Wi-Fi whenever wireless debugging is on.

From a terminal:

```bash
./gradlew :app:installDebug      # build and install on the connected phone or emulator
Scripts/install-on-phone.sh       # build and install on the phone, over the cable or Wi-Fi
./gradlew :app:testDebugUnitTest  # engine tests
./gradlew :app:bundleRelease      # the .aab for Google Play
```

## Publishing on Google Play

1. Create a developer account at [play.google.com/console](https://play.google.com/console) (a one-time fee).
2. Create an **upload key** once and keep it safe, outside this repository:

   ```bash
   keytool -genkeypair -v -keystore ~/sashaspuzzles-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
   ```

3. Next to this README, create `keystore.properties` (it is git-ignored):

   ```properties
   storeFile=/Users/you/sashaspuzzles-upload.jks
   storePassword=…
   keyAlias=upload
   keyPassword=…
   ```

4. `./gradlew :app:bundleRelease` writes `app/build/outputs/bundle/release/app-release.aab`; upload it in the
   Play Console. Google keeps the app-signing key itself (Play App Signing), so a lost upload key can be reset.

New personal developer accounts must run a closed test with at least 12 testers for 14 days before the app can be
published to everyone — check the current rules in the Play Console.

## Credits

Photographs from Unsplash, see [docs/photo-credits.md](docs/photo-credits.md). Fonts: Caprasimo and Figtree,
SIL Open Font License (`app/src/main/assets/licenses/`).
